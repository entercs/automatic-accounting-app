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
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = listOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
                .ifBlank { extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty() },
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ").orEmpty(),
        ).filter { it.isNotBlank() }.distinct().joinToString(" ")
        if (body.isBlank() || !PaymentParsers.isCandidate(pkg, title, body)) return
        // Do not use an old notification's amount as a new payment after reconnecting.
        if (System.currentTimeMillis() - postedAt > 5 * 60_000L) return
        val source = if (eventId == null) "无障碍通知" else "通知读取"
        submit(pkg, title, body, source, eventId) { PaymentParsers.parse(pkg, title, body)?.copy(payTime = postedAt) }
    }

    fun screen(pkg: String, content: String, parsed: ParsedNotification?) {
        // Keep screen logs to payment-like pages; do not retain ordinary browsing content.
        if (parsed == null && !Regex("支付成功|付款成功|交易成功|收款成功|退款成功").containsMatchIn(content)) return
        submit(pkg, "支付页面", content, "屏幕补充", null) { parsed }
    }

    private fun submit(pkg: String, title: String, text: String, source: String, eventId: String?, parse: () -> ParsedNotification?) {
        scope.launch {
            var parsed: ParsedNotification? = null
            try {
                parsed = parse()
                val result = if (parsed == null) "未识别" else mutex.withLock {
                    val value = parsed ?: return@withLock "未识别"
                    val now = System.currentTimeMillis()
                    if (deduplicator.isDuplicate(pkg, value, source, now)) return@withLock "重复已跳过"
                    val identity = eventId ?: "$pkg:$title:$text:${now / 10_000}"
                    val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
                    val app = FinanceTrackerApp.instance
                    val db = app.database
                    val outcome = PaymentRecorder(db).record(value, key, now)
                    if (outcome == "已记账") {
                        deduplicator.recorded(pkg, value, source, now)
                        notifyRecorded(value)
                    }
                    outcome
                }
                NotificationLogger.log(NotificationLogEntry(System.currentTimeMillis(), pkg, title, text, parsed != null, parsed?.amount, result, source))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Accounting", "Payment processing failed", e)
                NotificationLogger.log(NotificationLogEntry(System.currentTimeMillis(), pkg, title, text, parsed != null, parsed?.amount, "处理失败，待补记", source))
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
