package com.financetracker.notification

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.financetracker.MainActivity
import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.classifier.AutoClassifier
import com.financetracker.notification.parser.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FinanceNotificationService : AccessibilityService() {

    companion object { private const val TAG = "FT" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parsers: List<NotificationParser> = listOf(
        WeChatPayParser(),
        AlipayParser(),
        JDPayParser(),
        BankCardParser(),
    )
    private val classifier = AutoClassifier()
    private val screenParser = ScreenContentParser()

    private var lastScreenCaptureTime = 0L
    private var lastScreenCaptureHash = 0

    // Global dedup: prevent same amount from being recorded twice within 30s
    private val recentRecordedAmounts = mutableMapOf<Double, Long>()
    private val recentNotificationHashes = mutableMapOf<Int, Long>()
    private val dedupMutex = Mutex()

    // 通知内容去重窗口（毫秒）
    private val notifDedupWindowMs = 10_000L
    // 金额去重窗口（毫秒）
    private val amountDedupWindowMs = 30_000L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val isPaymentApp = parsers.any { packageName in it.supportedPackages }

        // 银行类app有管控，检测到无障碍会拒绝操作，跳过屏幕内容读取
        val isProtectedBank = packageName in protectedBankPackages
        if (isProtectedBank) {
            Log.d(TAG, "检测到受保护银行app，仅处理通知: $packageName")
            // 银行app只处理通知，不读取屏幕内容
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                val parser = parsers.firstOrNull { packageName in it.supportedPackages } ?: return
                handleNotification(event, parser, packageName)
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val parser = parsers.firstOrNull { packageName in it.supportedPackages } ?: return
                handleNotification(event, parser, packageName)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!isPaymentApp && packageName !in topScreenPackages) return
                if (isPaymentApp) Log.d(TAG, "窗口内容变化: $packageName")
                scope.launch { captureAndParse(packageName, initialDelayMs = 0) }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (!isPaymentApp && packageName !in topScreenPackages) return
                if (isPaymentApp) Log.d(TAG, "窗口状态变化: $packageName")
                scope.launch { captureAndParse(packageName, initialDelayMs = 100) }
            }
        }
    }

    // 银行类app有管控，检测到无障碍会拒绝操作，跳过屏幕内容读取
    private val protectedBankPackages = setOf(
        // 招商银行
        "com.cmbchina.ccs.pluto", "com.cmbc.ccs", "com.cmbchina.pocket", "cmb.pb",
        // 工商银行
        "com.icbc",
        // 建设银行
        "com.chinamworld.main",
        // 农业银行
        "com.android.bankabc",
        // 中国银行
        "com.chinamworld.bocmbci",
        // 交通银行
        "com.bankcomm.maidanba",
        // 邮储银行
        "com.psbc.mobilebank",
    )

    // Known app packages where payment may happen via in-app screens or webviews
    private val topScreenPackages = setOf(
        "com.eg.android.AlipayGphone", "com.eg.android.AlipayGphoneRC",
        "com.eg.android.AlipayGphoneGlobal", "hk.alipay.wallet",
        "com.tencent.mm", "com.jingdong.app.mall",
        "com.taobao.taobao", "com.tmall.wireless",
    )

    private fun accountTypeForPackage(packageName: String): String? = when {
        packageName.startsWith("com.eg.android.Alipay") || packageName == "hk.alipay.wallet" -> "alipay"
        packageName == "com.tencent.mm" -> "wechat"
        packageName == "com.jingdong.app.mall" -> "jd"
        else -> null
    }

    private fun handleNotification(event: AccessibilityEvent, parser: NotificationParser, packageName: String) {
        val notif = event.parcelableData as? Notification
        val title = notif?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notif?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = notif?.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val fullText = listOf(title, text, subText).filter { it.isNotBlank() }.joinToString(" ")
        val finalText = fullText.ifBlank {
            event.text.joinToString(" ") { it.toString() }
        }

        if (finalText.isBlank()) return

        // 内容去重：相同内容的通知在短时间内只处理一次
        val notifHash = finalText.hashCode()
        val now = System.currentTimeMillis()

        // 快速检查是否需要跳过（非原子，但可接受）
        val lastTime = recentNotificationHashes[notifHash]
        if (lastTime != null && now - lastTime < notifDedupWindowMs) {
            Log.d(TAG, "跳过重复通知: hash=$notifHash")
            return
        }
        recentNotificationHashes.entries.removeAll { now - it.value > notifDedupWindowMs }
        recentNotificationHashes[notifHash] = now

        scope.launch {
            val parsed = parser.parse(packageName, title, finalText)
            // Log all notification attempts
            NotificationLogger.log(NotificationLogEntry(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                title = title,
                text = finalText,
                parsed = parsed != null,
                parsedAmount = parsed?.amount,
            ))
            if (parsed != null) {
                recordTransaction(parsed, packageName)
                showNotification("已自动记账", "${parsed.merchant} ${String.format("%.2f", parsed.amount)}")
            } else {
                val hasPayHint = listOf("支付", "交易", "付款", "账单").any { it in title || it in text }
                if (hasPayHint) {
                    showReminderNotification(packageName)
                }
            }
        }
    }

    private suspend fun captureAndParse(packageName: String, initialDelayMs: Long) {
        if (initialDelayMs > 0) delay(initialDelayMs)
        val root = rootInActiveWindow ?: run {
            Log.d(TAG, "rootInActiveWindow 为 null ($packageName)")
            return
        }
        try {
            handleWindowContent(root, packageName)
        } finally {
            root.recycle()
        }
    }

    private suspend fun handleWindowContent(root: android.view.accessibility.AccessibilityNodeInfo, packageName: String) {
        val now = System.currentTimeMillis()

        val screenTexts = listNotNullTexts(root)
        val contentHash = screenTexts.hashCode()
        if (now - lastScreenCaptureTime < 2000 && contentHash == lastScreenCaptureHash) return

        val parsed = screenParser.parse(packageName, root)
        NotificationLogger.log(NotificationLogEntry(
            timestamp = now,
            packageName = packageName,
            title = "[屏幕内容]",
            text = screenTexts.take(10).joinToString(" | "),
            parsed = parsed != null,
            parsedAmount = parsed?.amount,
        ))
        if (parsed != null) {
            Log.d(TAG, "屏幕解析成功: $packageName → ${parsed.merchant} ${parsed.amount}")
            lastScreenCaptureTime = now
            lastScreenCaptureHash = contentHash
            recordTransaction(parsed, packageName)
            showNotification("已自动记账(屏幕)", "${parsed.merchant} ${String.format("%.2f", parsed.amount)}")
        } else {
            val hasKw = screenParser.hasPayKeywords(screenTexts)
            Log.d(TAG, "屏幕解析失败: $packageName, 文本数=${screenTexts.size}, 有关键词=$hasKw, 首5条=${screenTexts.take(5)}")
        }
    }

    private fun listNotNullTexts(node: android.view.accessibility.AccessibilityNodeInfo?): List<String> {
        val result = mutableListOf<String>()
        if (node == null) return result
        collectNodeText(node, result, 50)
        return result
    }

    private fun collectNodeText(node: android.view.accessibility.AccessibilityNodeInfo, result: MutableList<String>, max: Int) {
        if (result.size >= max) return
        node.text?.toString()?.trim()?.takeIf { it.length > 1 }?.let { result.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.length > 1 }?.let { result.add(it) }
        for (i in 0 until node.childCount) {
            if (result.size >= max) break
            node.getChild(i)?.let { collectNodeText(it, result, max) }
        }
    }

    private suspend fun recordTransaction(
        parsed: com.financetracker.domain.model.ParsedNotification,
        packageName: String,
    ) {
        val now = System.currentTimeMillis()

        // Atomic dedup: only one coroutine can pass for the same amount
        val shouldRecord = dedupMutex.withLock {
            val lastTime = recentRecordedAmounts[parsed.amount]
            if (lastTime != null && now - lastTime < amountDedupWindowMs) return
            recentRecordedAmounts.entries.removeAll { now - it.value > 60_000 }
            recentRecordedAmounts[parsed.amount] = now
            true
        }

        val db = com.financetracker.FinanceTrackerApp.instance.database
        // Match account by package name, fall back to parser's accountType
        val accountType = accountTypeForPackage(packageName) ?: parsed.accountType
        val account = db.paymentAccountDao().getByType(accountType) ?: return
        val categoryId = classifier.classify(parsed.merchant)

        val transaction = com.financetracker.domain.model.Transaction(
            amount = parsed.amount,
            type = parsed.transactionType,
            categoryId = categoryId,
            accountId = account.id,
            merchant = parsed.merchant,
            date = parsed.payTime,
            source = "notification",
        )
        com.financetracker.di.AppModule.transactionRepository.add(transaction)
    }

    private fun showNotification(title: String, content: String) {
        val channelId = "auto_accounting"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "自动记账通知", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun showReminderNotification(packageName: String) {
        val appName = when (packageName) {
            "com.tencent.mm" -> "微信"
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.jingdong.app.mall" -> "京东"
            else -> "支付应用"
        }
        val channelId = "reminder"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "记账提醒", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to_add", true) }
        val pi = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("检测到${appName}支付通知")
            .setContentText("点击手动记账")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt() + 1, notif)
    }

    override fun onInterrupt() {}
}
