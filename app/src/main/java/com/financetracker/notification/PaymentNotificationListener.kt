package com.financetracker.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** System-managed notification delivery; accessibility is optional. Never replay old notifications on connect. */
class PaymentNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        PaymentMonitor.connected(this, true)
        NotificationLogger.service(true, "通知通道已连接，将接收新的支付通知；连接前的旧通知不会自动补记")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            AccountingEngine.notification(sbn.packageName, sbn.notification, "${sbn.key}:${sbn.postTime}", sbn.postTime)
        } catch (e: Exception) {
            android.util.Log.e("Accounting", "Notification extraction failed", e)
            if (com.financetracker.notification.parser.PaymentParsers.supports(sbn.packageName)) {
                NotificationLogger.log(NotificationLogEntry(System.currentTimeMillis(), sbn.packageName, "通知回调异常", "", false,
                    outcome = "处理失败", stage = "通知接收", reason = e.javaClass.simpleName))
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        PaymentMonitor.connected(this, false)
        NotificationLogger.service(false, "系统通知连接已断开，已请求重新连接；此期间的交易请核对账本")
        PaymentMonitor.reconnect(this)
    }

    override fun onDestroy() {
        if (PaymentMonitor.status.value.connected) NotificationLogger.service(false, "通知监听服务已结束；系统直接终止进程时可能来不及留下此记录")
        PaymentMonitor.connected(this, false)
        super.onDestroy()
    }
}
