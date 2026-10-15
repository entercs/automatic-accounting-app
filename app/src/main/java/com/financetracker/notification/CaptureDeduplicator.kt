package com.financetracker.notification

import com.financetracker.domain.model.ParsedNotification

/** Short-lived bridge between two channels. Distinct notifications of equal value remain valid. */
class CaptureDeduplicator {
    private data class Entry(val pkg: String, val payment: ParsedNotification, val channel: String, val time: Long)
    private val entries = mutableListOf<Entry>()
    fun isDuplicate(pkg: String, payment: ParsedNotification, channel: String, now: Long): Boolean {
        entries.removeAll { now - it.time >= 12_000 }
        return entries.any {
            it.pkg == pkg && it.payment.amount == payment.amount && it.payment.transactionType == payment.transactionType &&
                it.payment.merchant.trim() == payment.merchant.trim() &&
                (it.channel != channel || channel == "屏幕补充")
        }
    }
    fun recorded(pkg: String, payment: ParsedNotification, channel: String, now: Long) {
        entries.add(Entry(pkg, payment, channel, now))
    }
}
