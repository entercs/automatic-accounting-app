package com.financetracker.notification

import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class CaptureDeduplicatorTest {
    private val payment = ParsedNotification(20.0, "小店", "wechat", TransactionType.EXPENSE, 0)
    @Test fun twoNotificationsWithSameAmountAreNotCollapsed() {
        val d = CaptureDeduplicator(); d.recorded("wechat", payment, "通知读取", 1000)
        assertFalse(d.isDuplicate("wechat", payment, "通知读取", 2000))
    }
    @Test fun sameCaptureAcrossChannelsIsSkipped() {
        val d = CaptureDeduplicator(); d.recorded("wechat", payment, "通知读取", 1000)
        assertTrue(d.isDuplicate("wechat", payment, "屏幕补充", 2000))
    }
    @Test fun differentMerchantsAndAppsAreIndependent() {
        val d = CaptureDeduplicator(); d.recorded("wechat", payment, "通知读取", 1000)
        assertFalse(d.isDuplicate("wechat", payment.copy(merchant = "另一家店"), "屏幕补充", 2000))
        assertFalse(d.isDuplicate("alipay", payment, "屏幕补充", 2000))
    }
    @Test fun refundAndExpenseHaveDifferentIdentity() {
        val d = CaptureDeduplicator(); d.recorded("wechat", payment, "通知读取", 1000)
        assertFalse(d.isDuplicate("wechat", payment.copy(transactionType = TransactionType.INCOME), "屏幕补充", 2000))
    }
    @Test fun bridgeWindowExpires() {
        val d = CaptureDeduplicator(); d.recorded("wechat", payment, "通知读取", 1000)
        assertFalse(d.isDuplicate("wechat", payment, "屏幕补充", 14000))
    }
}
