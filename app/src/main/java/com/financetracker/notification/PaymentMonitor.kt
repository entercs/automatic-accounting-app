package com.financetracker.notification

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MonitorStatus(val authorized: Boolean = false, val connected: Boolean = false)

object PaymentMonitor {
    private val state = MutableStateFlow(MonitorStatus())
    val status = state.asStateFlow()

    fun refresh(context: Context) {
        val component = ComponentName(context, PaymentNotificationListener::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
        state.value = state.value.copy(authorized = enabled, connected = enabled && state.value.connected)
    }

    fun connected(context: Context, value: Boolean) {
        refresh(context)
        state.value = state.value.copy(connected = value && state.value.authorized)
    }

    fun reconnect(context: Context) {
        refresh(context)
        if (state.value.authorized && !state.value.connected) {
            NotificationListenerService.requestRebind(ComponentName(context, PaymentNotificationListener::class.java))
        }
    }
}
