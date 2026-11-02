package com.financetracker.notification

import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.parser.PaymentParsers
import org.junit.Assert.*
import org.junit.Test

class JDFinanceParsingTest {
    private val pkg = "com.jd.jrapp"
    private val title = "白条交易提醒"
    private val sample = "【未分期】您于11月2日消费8.49元，查看详情>>"

    @Test fun realBaitiaoNotificationEntersCaptureAndPreservesAmountAndMethod() {
        assertTrue(PaymentParsers.supports(pkg))
        assertTrue(PaymentParsers.isCandidate(pkg, title, sample))
        val result = PaymentParsers.parse(pkg, title, sample)!!
        assertEquals(8.49, result.amount, .00001)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals("京东", result.merchant)
        assertEquals("jd", result.accountType)
        assertEquals("白条", result.paymentMethod)
    }

    @Test fun testerAndFiltersDistinguishFinanceFromMall() {
        val app = RecognitionApps.find(pkg)!!
        assertEquals("京东金融", app.label)
        assertEquals(title, app.defaultTitle)
        val entry = NotificationLogEntry(0, pkg, title, sample, false)
        assertEquals("京东金融", entry.appLabel)
        assertTrue(RecognitionFilter(applications = setOf(app.id)).matches(entry))
        assertFalse(RecognitionFilter(applications = setOf("jd")).matches(entry))
    }

    @Test fun mallBaitiaoNotificationsStillWork() {
        val result = PaymentParsers.parse("com.jingdong.app.mall", title, sample)!!
        assertEquals(8.49, result.amount, .00001)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals("jd", result.accountType)
    }

    @Test fun completedRefundUsesRefundAmountAndIncomeDirection() {
        val result = PaymentParsers.parse(pkg, "白条退款成功", "原支付金额100.00元，退款金额8.49元，已退回白条")!!
        assertEquals(8.49, result.amount, .00001)
        assertEquals(TransactionType.INCOME, result.transactionType)
        assertEquals("白条", result.paymentMethod)
    }

    @Test fun returnLinkDoesNotTurnConsumptionIntoIncome() {
        assertEquals(TransactionType.EXPENSE, PaymentParsers.parse(pkg, title, "$sample 返回京东")!!.transactionType)
    }

    @Test fun repaymentAndCreditLimitNoticesDoNotBecomeNewSpending() {
        listOf(
            "白条还款成功" to "您已还款8.49元",
            "白条还款提醒" to "本期应还8.49元，请及时还款",
            "白条额度变动提醒" to "您的白条额度已提升至8000元",
            "白条账单" to "本期消费8.49元，待还8.49元",
        ).forEach { (heading, text) -> assertNull(heading, PaymentParsers.parse(pkg, heading, text)) }
    }

    @Test fun loansInvestmentsAndPromotionsAreNotBaitiaoConsumption() {
        assertFalse(PaymentParsers.isCandidate(pkg, "金条到账通知", "借款成功，到账8000元"))
        assertFalse(PaymentParsers.isCandidate(pkg, "理财收益到账", "理财收益到账8.49元"))
        listOf(
            "金条到账通知" to "借款成功，到账8000元",
            "理财收益到账" to "理财收益到账8.49元",
            "白条优惠提醒" to "支付立减8.49元，领取优惠券",
            "白条额度" to "消费享优惠，额度10000元",
        ).forEach { (heading, text) -> assertNull(heading, PaymentParsers.parse(pkg, heading, text)) }
    }

    @Test fun failedPendingAndUnfinishedRefundsCannotBeRecorded() {
        listOf("支付失败", "待支付", "退款申请", "退款处理中", "退款失败").forEach { state ->
            assertNull(state, PaymentParsers.parse(pkg, title, "$sample $state"))
        }
    }
}
