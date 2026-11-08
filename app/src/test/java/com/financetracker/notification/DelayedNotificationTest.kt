package com.financetracker.notification

import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.parser.PaymentParsers
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DelayedNotificationTest {
    private data class Sample(val pkg: String, val title: String, val body: String, val published: String, val amount: String, val accountType: String, val merchant: String)
    private fun time(value: String) = LocalDateTime.parse("2026-11-06 $value", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()

    @Test fun bothScreenshotTransactionsRemainEligibleAfterTwoHourDeliveryDelay() {
        val received = time("22:26:25")
        val started = time("18:00:00")
        val samples = listOf(
            Sample("cmb.pb", "招商银行", "您账户2741于11月06日20:09在【抖音支付-抖音生活服务/所见所得-北京…】发生快捷支付扣款，人民币80.90", "20:09:56", "80.90", "bank", "抖音"),
            Sample("com.eg.android.AlipayGphone", "交易提醒", "你在北京一卡通有一笔4.00元的免密/自动扣款支付，点击领取8个支付宝积分。", "20:19:33", "4.00", "alipay", "北京一卡通"),
        )
        samples.forEach { (pkg, title, body, published, amount, accountType, merchant) ->
            val decision = NotificationPreflight.check(false, body, PaymentParsers.isCandidate(pkg, title, body), time(published), received, started)
            assertNull(decision.reason)
            val parsed = PaymentParsers.parse(pkg, title, body)!!
            assertEquals(amount.toDouble(), parsed.amount, .00001)
            assertEquals(TransactionType.EXPENSE, parsed.transactionType)
            assertEquals(accountType, parsed.accountType)
            assertEquals(merchant, parsed.merchant)
        }
    }

    @Test fun aNoticeFromBeforeCaptureStartedRequiresReviewEvenIfOnlySecondsOld() {
        val decision = NotificationPreflight.check(false, "消费8元", true, time("20:00:00"), time("20:00:03"), time("20:00:01"))
        assertTrue(decision.needsReview)
        assertTrue(decision.reason!!.contains("采集启用"))
    }

    @Test fun oneDayBoundaryAcceptsDelayedCurrentCaptureAndReviewsOlderEvents() {
        val now = time("22:26:25")
        assertNull(NotificationPreflight.check(false, "消费8元", true, now - NotificationPreflight.MAX_DELAY_MILLIS, now, now - 2 * NotificationPreflight.MAX_DELAY_MILLIS).reason)
        val tooOld = NotificationPreflight.check(false, "消费8元", true, now - NotificationPreflight.MAX_DELAY_MILLIS - 1, now)
        assertTrue(tooOld.needsReview)
        assertTrue(tooOld.reason!!.contains("24小时"))
    }

    @Test fun invalidFutureTimestampRequiresReview() {
        val now = time("22:26:25")
        assertTrue(NotificationPreflight.check(false, "消费8元", true, now + 60_001, now).needsReview)
        assertTrue(NotificationPreflight.check(false, "消费8元", true, 0, now).needsReview)
    }

    @Test fun nonTransactionPrivacyAndSummaryDeduplicationPrecedeDelayHandling() {
        val now = time("22:26:25")
        val old = time("20:00:00")
        val summary = NotificationPreflight.check(true, "消费8元", true, old, now)
        assertFalse(summary.needsReview)
        assertTrue(summary.reason!!.contains("分组汇总"))
        val ordinary = NotificationPreflight.check(false, "普通消息", false, old, now)
        assertFalse(ordinary.needsReview)
        assertTrue(ordinary.reason!!.contains("不符合支付"))
    }
}
