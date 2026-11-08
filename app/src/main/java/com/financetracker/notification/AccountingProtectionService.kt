package com.financetracker.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.financetracker.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/** User-visible background protection; it never reads or replays existing notifications. */
class AccountingProtectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recovery: Job? = null
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "自动记账后台保护", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持自动记账在后台运行，并在通知读取断开时尝试恢复"
                setShowBadge(false)
            },
        )
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, notification())
            promoted = true
            PaymentMonitor.protectionRunning(true)
        } catch (e: Exception) {
            PaymentMonitor.protectionIssue("后台保护启动受限：${e.javaClass.simpleName}，请检查后台权限并重新打开应用")
            stopSelf()
            return
        }
        scope.launch {
            PaymentMonitor.status.map { it.connected to it.authorized }.distinctUntilChanged().collect {
                if (promoted) runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            PaymentMonitor.setProtectionEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        PaymentMonitor.refresh(this)
        val state = PaymentMonitor.status.value
        if (!promoted || !state.authorized || !state.protectionEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (recovery?.isActive != true) recovery = scope.launch {
            while (isActive) {
                PaymentMonitor.refresh(this@AccountingProtectionService)
                val current = PaymentMonitor.status.value
                if (!current.authorized || !current.protectionEnabled) {
                    stopSelf()
                    break
                }
                PaymentMonitor.reconnect(this@AccountingProtectionService)
                withTimeoutOrNull(PaymentMonitor.recoveryDelayMillis()) {
                    PaymentMonitor.status.first { it.connected != current.connected || it.authorized != current.authorized || it.protectionEnabled != current.protectionEnabled }
                }
            }
        }
        return START_STICKY
    }

    private fun notification(): Notification {
        val state = PaymentMonitor.status.value
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 11, Intent(this, AccountingProtectionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("自动记账后台保护中")
            .setContentText(if (state.connected) "通知读取已连接，等待支付通知" else "正在恢复通知读取，请允许后台运行")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "关闭后台保护", stop).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        promoted = false
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        PaymentMonitor.protectionRunning(false)
        super.onDestroy()
    }

    companion object {
        private const val ID = 1002
        private const val CHANNEL = "accounting_background_protection"
        private const val ACTION_STOP = "com.financetracker.STOP_BACKGROUND_PROTECTION"

        fun ensureRunning(context: Context) {
            PaymentMonitor.refresh(context)
            val state = PaymentMonitor.status.value
            if (!state.authorized || !state.protectionEnabled) {
                context.stopService(Intent(context, AccountingProtectionService::class.java))
                return
            }
            if (state.protectionRunning) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, AccountingProtectionService::class.java))
            } catch (e: Exception) {
                PaymentMonitor.protectionIssue("系统暂未允许启动后台保护：${e.javaClass.simpleName}。请打开应用并允许自启动、无限制省电")
            }
        }
    }
}

class AccountingStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AccountingProtectionService.ensureRunning(context)
        }
    }
}
