package com.financetracker.notification

import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.parser.PaymentParsers
import org.junit.Assert.*
import org.junit.Test

class AlipayReminderTest {
    private val pkg = "com.eg.android.AlipayGphone"
    @Test fun screenshotTransitPaymentWithPointsIsParsed() {
        val result = PaymentParsers.parse(pkg, "交易提醒", "你在北京一卡通有一笔0.50元的免密/自动扣款支付，点击领取8个支付宝积分。")!!
        assertEquals(.50, result.amount, .001)
        assertEquals("北京一卡通", result.merchant)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
    }
    @Test fun screenshotExpenseUsesTransactionAmountRatherThanPromotionalTwoYuan() {
        val result = PaymentParsers.parse(pkg, "交易提醒", "你有一笔16.90元的支出，点击领取2元流量红包。使用花呗支付，确认收货的订单请及时还款。")!!
        assertEquals(16.90, result.amount, .001)
        assertEquals("花呗", result.paymentMethod)
        assertEquals("支付宝", result.merchant)
    }
    @Test fun completedExpenseSurvivesUnclaimedCouponAndRepaymentPromotion() {
        assertEquals(16.90, PaymentParsers.parse(pkg, "交易提醒", "你有一笔 16.90 元的支出，2元红包待领取。花呗还款提醒请查看账单。")!!.amount, .001)
    }
    @Test fun completedPaymentSurvivesExpiredCouponTail() {
        assertEquals(.50, PaymentParsers.parse(pkg, "交易提醒", "你在北京一卡通有一笔0.50元的自动扣款支付，红包即将失效。")!!.amount, .001)
    }
    @Test fun strongTitleDoesNotTurnAnUnclaimedCouponIntoAnExpense() {
        assertNull(PaymentParsers.parse(pkg, "交易提醒", "你有一笔2元红包待领取"))
        assertNull(PaymentParsers.parse(pkg, "交易提醒", "花呗还款提醒，待还16.90元"))
    }
    @Test fun actualFailureStillOverridesTrustedTitleAndExpensePhrase() {
        assertNull(PaymentParsers.parse(pkg, "交易提醒", "你有一笔16.90元的支出，支付失败，请重试"))
        assertNull(PaymentParsers.parse(pkg, "交易提醒", "退款处理中16.90元"))
    }
    @Test fun sourceSpecificTitleMismatchIsExplained() {
        assertNull(PaymentParsers.parse("com.tencent.mm", "交易提醒", "你有一笔16.90元的支出"))
        assertTrue(PaymentParsers.failureReason("com.tencent.mm", "交易提醒", "你有一笔16.90元的支出").contains("标题"))
        assertTrue(PaymentParsers.failureReason("example.unknown", "交易提醒", "支出16元").contains("尚未适配"))
    }
    @Test fun incomeDirectionSurvivesPaymentMethodInPromotionalTail() {
        val result = PaymentParsers.parse(pkg, "交易提醒", "你有一笔16.90元的收入，点击领取2元红包。支持花呗支付。")!!
        assertEquals(TransactionType.INCOME, result.transactionType)
        assertNull("支持花呗的推广文案不能当作实际付款方式", result.paymentMethod)
    }
}
