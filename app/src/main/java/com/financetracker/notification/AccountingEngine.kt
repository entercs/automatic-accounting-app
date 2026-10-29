package com.financetracker.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.financetracker.FinanceTrackerApp
import com.financetracker.MainActivity
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.notification.parser.PaymentParsers
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** Both capture channels share parsing, serialization and transactional recording. */
object AccountingEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val deduplicator = CaptureDeduplicator()

    fun notification(pkg: String, notification: Notification, eventId: String?, postedAt: Long = System.currentTimeMillis()) {
        if (!PaymentParsers.supports(pkg)) return
        val receivedAt = System.currentTimeMillis()
        val source = if (eventId == null) "无障碍通知" else "通知读取"
        try {
            val payload = NotificationPayload.read(notification)
            val candidate = PaymentParsers.isCandidate(pkg, payload.title, payload.body)
            val skipped = NotificationPreflight.skipReason(
                notification.flags and Notification.FLAG_GROUP_SUMMARY != 0, payload.body, candidate, postedAt, receivedAt,
            )
            val entry = NotificationLogEntry(receivedAt, pkg,
                if (candidate) payload.title else "非交易通知", if (candidate) payload.body else "", false,
                outcome = if (skipped == null) "处理中" else "已跳过", source = source,
                reason = skipped ?: "已收到通知，正在解析", stage = "通知接收", postedAt = postedAt)
            NotificationLogger.log(entry)
            if (skipped != null) return
            submit(entry, eventId) { PaymentParsers.parse(pkg, payload.title, payload.body)?.copy(payTime = postedAt) }
        } catch (e: Exception) {
            Log.e("Accounting", "Notification extraction failed", e)
            NotificationLogger.log(NotificationLogEntry(receivedAt, pkg, "通知读取异常", "", false,
                outcome = "提取失败", source = source, reason = "提取通知字段失败：${e.javaClass.simpleName}", stage = "通知接收", postedAt = postedAt))
        }
    }

    fun screen(pkg: String, content: String, parsed: ParsedNotification?) {
        if (!ScreenCapturePolicy.allows(pkg)) return
        if (parsed == null && !Regex("支付成功|付款成功|交易成功|收款成功|退款成功").containsMatchIn(content)) return
        val entry = NotificationLogEntry(System.currentTimeMillis(), pkg, "支付页面", content, false,
            outcome = "处理中", source = "屏幕补充", stage = "屏幕识别")
        NotificationLogger.log(entry)
        submit(entry, null) { parsed }
    }

    private fun submit(entry: NotificationLogEntry, eventId: String?, parse: () -> ParsedNotification?) {
        scope.launch {
            var parsed: ParsedNotification? = null
            try {
                parsed = parse()
                val value = parsed
                val result = if (value == null) "未识别" else mutex.withLock {
                    NotificationLogger.update(entry.id) { it.copy(stage = "入账", reason = "已解析，正在核对账户与重复事件") }
                    val now = System.currentTimeMillis()
                    if (deduplicator.isDuplicate(entry.packageName, value, entry.source, now)) return@withLock "重复已跳过"
                    val identity = eventId ?: "${entry.packageName}:${entry.title}:${entry.text}:${now / 10_000}"
                    val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
                    val outcome = PaymentRecorder(FinanceTrackerApp.instance.database).record(value, key, now)
                    if (outcome == "已记账") {
                        deduplicator.recorded(entry.packageName, value, entry.source, now)
                        notifyRecorded(value)
                    }
                    outcome
                }
                NotificationLogger.update(entry.id) { it.copy(
                    parsed = value != null, parsedAmount = value?.amount, outcome = result,
                    stage = if (value == null) "解析" else "入账",
                    reason = when {
                        value == null && entry.source == "屏幕补充" -> "支付页面暂未匹配到有效金额和方向"
                        value == null -> PaymentParsers.failureReason(entry.packageName, entry.title, entry.text)
                        result == "重复已跳过" -> "与已处理事件或另一采集通道重复，未重复调整余额"
                        result == "已记账" -> "账单、账户余额与事件回执已一并保存"
                        else -> result
                    }, merchant = value?.merchant, transactionType = value?.transactionType, paymentMethod = value?.paymentMethod,
                ) }
            } catch (e: CancellationException) {
                NotificationLogger.update(entry.id) { it.copy(outcome = "结果待核对", reason = "处理被中断，请先核对账本再补记") }
                throw e
            } catch (e: Exception) {
                Log.e("Accounting", "Payment processing failed", e)
                NotificationLogger.update(entry.id) { it.copy(parsed = parsed != null, parsedAmount = parsed?.amount,
                    outcome = "处理失败", reason = "处理异常：${e.javaClass.simpleName}，请先核对账本再补记") }
            }
        }
    }

    private fun notifyRecorded(value: ParsedNotification) {
        // Notification permission failure must not change a successful database outcome.
        runCatching {
            val context = FinanceTrackerApp.instance
            if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("auto_accounting", "记账结果", NotificationManager.IMPORTANCE_LOW))
            val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(1001, NotificationCompat.Builder(context, "auto_accounting")
                .setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle("已自动记账")
                .setContentText("${value.merchant} · ¥%.2f".format(value.amount)).setContentIntent(pending).setAutoCancel(true).build())
        }
    }
}
