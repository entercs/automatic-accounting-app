package com.financetracker.notification

import android.app.Notification

data class NotificationPayload(val title: String, val body: String) {
    companion object {
        fun read(notification: Notification): NotificationPayload {
            val extras = notification.extras
            return NotificationPayload(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                listOf(
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
                    extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ").orEmpty(),
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n"))
        }
    }
}
