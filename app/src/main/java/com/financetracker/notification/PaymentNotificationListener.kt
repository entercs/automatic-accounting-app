package com.financetracker.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** System-managed notification delivery; accessibility is optional. Never replay old notifications on connect. */
class PaymentNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        PaymentMonitor.connected(this, true)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        try {
            AccountingEngine.notification(sbn.packageName, sbn.notification, "${sbn.key}:${sbn.postTime}", sbn.postTime)
        } catch (e: Exception) {
            android.util.Log.e("Accounting", "Notification extraction failed", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        PaymentMonitor.connected(this, false)
        PaymentMonitor.reconnect(this)
    }

    override fun onDestroy() {
        PaymentMonitor.connected(this, false)
        super.onDestroy()
    }
}
