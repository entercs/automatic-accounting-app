package com.financetracker.notification

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MonitorStatus(
    val authorized: Boolean = false,
    val connected: Boolean = false,
    val captureStartedAt: Long? = null,
    val protectionEnabled: Boolean = true,
    val protectionRunning: Boolean = false,
    val protectionIssue: String? = null,
)

object PaymentMonitor {
    private val state = MutableStateFlow(MonitorStatus())
    val status = state.asStateFlow()
    private val backoff = ListenerRecoveryBackoff()
    private var lastRecoveryLogAt = Long.MIN_VALUE

    @Synchronized fun refresh(context: Context) {
        val component = ComponentName(context, PaymentNotificationListener::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
        val captureStartedAt = NotificationCaptureSession.forContext(context).update(enabled)
        state.value = state.value.copy(
            authorized = enabled, connected = enabled && state.value.connected,
            captureStartedAt = captureStartedAt,
            protectionEnabled = NotificationCaptureSession.preferences(context).getBoolean("background_protection", true),
        )
        if (!enabled) backoff.reset()
    }

    @Synchronized fun connected(context: Context, value: Boolean) {
        val changed = state.value.connected != value
        refresh(context)
        state.value = state.value.copy(connected = value && state.value.authorized)
        if (value || changed) backoff.reset()
    }

    @Synchronized fun reconnect(context: Context) {
        refresh(context)
        if (!state.value.authorized || state.value.connected) return
        val now = SystemClock.elapsedRealtime()
        if (backoff.remainingMillis(now) > 0) return
        backoff.requested(now)
        try {
            NotificationListenerService.requestRebind(ComponentName(context, PaymentNotificationListener::class.java))
            if (backoff.attempts == 1 || lastRecoveryLogAt == Long.MIN_VALUE || now - lastRecoveryLogAt >= 300_000) {
                lastRecoveryLogAt = now
                NotificationLogger.runtime("正在恢复通知连接", "已请求系统重连，第${backoff.attempts}次尝试；后台保护开启时会继续重试")
            }
        } catch (e: Exception) {
            if (backoff.attempts == 1 || lastRecoveryLogAt == Long.MIN_VALUE || now - lastRecoveryLogAt >= 300_000) {
                lastRecoveryLogAt = now
                NotificationLogger.runtime("通知重连请求失败", "${e.javaClass.simpleName}；保留后续重试，请检查通知授权与后台限制")
            }
        }
    }

    @Synchronized fun recoveryDelayMillis(): Long = if (state.value.connected) 30_000 else backoff.remainingMillis(SystemClock.elapsedRealtime()).coerceAtLeast(1_000)

    @Synchronized fun protectionRunning(value: Boolean) {
        state.value = state.value.copy(protectionRunning = value, protectionIssue = if (value) null else state.value.protectionIssue)
    }

    @Synchronized fun protectionIssue(reason: String) {
        if (state.value.protectionIssue != reason) NotificationLogger.runtime("后台保护未运行", reason)
        state.value = state.value.copy(protectionRunning = false, protectionIssue = reason)
    }

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        NotificationCaptureSession.preferences(context).edit().putBoolean("background_protection", enabled).apply()
        state.value = state.value.copy(protectionEnabled = enabled, protectionIssue = null)
        if (enabled) AccountingProtectionService.ensureRunning(context)
        else context.stopService(android.content.Intent(context, AccountingProtectionService::class.java))
    }
}
