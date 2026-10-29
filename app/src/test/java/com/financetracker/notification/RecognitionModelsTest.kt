package com.financetracker.notification

import com.financetracker.notification.parser.PaymentParsers
import org.junit.Assert.*
import org.junit.Test

class RecognitionModelsTest {
    private val now = 2_000_000_000_000L
    private fun entry(pkg: String = "com.eg.android.AlipayGphone", time: Long = now, outcome: String = "未识别", channel: String = "通知读取") =
        NotificationLogEntry(time, pkg, "交易提醒", "样本", false, outcome = outcome, source = channel)

    @Test fun dropdownCatalogMatchesAllSupportedPackagesWithoutAddingUnsupportedSources() {
        assertEquals(PaymentParsers.packages.toSet(), RecognitionApps.apps.flatMap { it.packages }.toSet())
        assertEquals(RecognitionApps.apps.size, RecognitionApps.apps.map { it.id }.distinct().size)
    }
    @Test fun applicationFilterIncludesAliasesAndCombinesMultipleApps() {
        val f = RecognitionFilter(applications = setOf("alipay", "wechat"))
        assertTrue(f.matches(entry("com.eg.android.AlipayGphoneRC")))
        assertTrue(f.matches(entry("com.tencent.mm")))
        assertFalse(f.matches(entry("cmb.pb")))
    }
    @Test fun allFilterIncludesServiceAndSkippedEvents() {
        assertTrue(RecognitionFilter().matches(entry(NotificationLogEntry.SERVICE_PACKAGE, outcome = "服务动态")))
        assertTrue(RecognitionFilter().matches(entry(outcome = "已跳过")))
    }
    @Test fun filtersIntersectApplicationResultChannelAndTime() {
        val f = RecognitionFilter(setOf("alipay"), setOf(RecognitionStatus.UNRECOGNIZED, RecognitionStatus.PENDING), setOf("通知读取"), now - 100, now + 100)
        assertTrue(f.matches(entry()))
        assertTrue(f.matches(entry(outcome = "待补记：账户已停用")))
        assertFalse(f.matches(entry(outcome = "已记账")))
        assertFalse(f.matches(entry(channel = "屏幕补充")))
        assertFalse(f.matches(entry("com.tencent.mm")))
        assertFalse(f.matches(entry(time = now - 101)))
    }
    @Test fun customDateRangeIncludesStartAndExcludesNextMidnight() {
        val f = RecognitionFilter(start = now, endExclusive = now + 100)
        assertTrue(f.matches(entry(time = now)))
        assertTrue(f.matches(entry(time = now + 99)))
        assertFalse(f.matches(entry(time = now + 100)))
    }
    @Test fun attentionIncludesFailuresAndPendingWithoutCallingEverySkipATransaction() {
        val f = RecognitionFilter(attentionOnly = true)
        listOf("未识别", "待补记：请先添加账户", "处理失败", "结果待核对").forEach { assertTrue(f.matches(entry(outcome = it))) }
        listOf("已记账", "重复已跳过", "已跳过", "服务动态", "处理中").forEach { assertFalse(f.matches(entry(outcome = it))) }
    }
    @Test fun retentionKeepsBoundaryAndSortsArrivalTimes() {
        val old = entry(time = now - RecognitionRetention.MAX_AGE - 1)
        val boundary = entry(time = now - RecognitionRetention.MAX_AGE)
        assertEquals(listOf(now, boundary.timestamp), RecognitionRetention.keep(listOf(old, boundary, entry()), now).map { it.timestamp })
    }
    @Test fun retentionCapsNewestThousandAndDoesNotInventOlderCoverage() {
        val values = (0..1200).map { entry(time = now - it) }
        val kept = RecognitionRetention.keep(values.reversed(), now)
        assertEquals(1000, kept.size)
        assertEquals(now, kept.first().timestamp)
        assertEquals(now - 999, kept.last().timestamp)
    }
    @Test fun groupSummaryReasonPreventsDuplicateProcessing() {
        assertTrue(NotificationPreflight.skipReason(true, "支出16元", true, now, now)!!.contains("分组汇总"))
    }
    @Test fun emptyAndNonPaymentNotificationsHaveDifferentReasons() {
        assertTrue(NotificationPreflight.skipReason(false, "", true, now, now)!!.contains("正文为空"))
        assertTrue(NotificationPreflight.skipReason(false, "普通聊天", false, now, now)!!.contains("不符合支付"))
    }
    @Test fun ageChecksNotificationArrivalRatherThanTimeUserViewsTheLog() {
        assertNull(NotificationPreflight.skipReason(false, "支出", true, now - 300_000, now))
        assertTrue(NotificationPreflight.skipReason(false, "支出", true, now - 300_001, now)!!.contains("五分钟"))
    }
    @Test fun serviceEntryIsClearlyLabelledAndNotAPaymentSource() {
        assertEquals("通知服务", entry(NotificationLogEntry.SERVICE_PACKAGE).appLabel)
        assertEquals(RecognitionStatus.SERVICE, entry(outcome = "服务动态").status)
    }
}
