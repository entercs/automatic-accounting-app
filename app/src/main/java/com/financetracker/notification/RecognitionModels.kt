package com.financetracker.notification

import com.financetracker.domain.calculator.Money
import com.financetracker.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class RecognitionStatus(val label: String) {
    PROCESSING("处理中"), RECORDED("已记账"), UNRECOGNIZED("未识别"), PENDING("待补记"),
    SKIPPED("已跳过"), FAILED("处理失败"), SERVICE("服务动态");

    companion object {
        fun of(outcome: String) = when {
            outcome == "已记账" -> RECORDED
            outcome == "处理中" -> PROCESSING
            outcome == "未识别" -> UNRECOGNIZED
            outcome.startsWith("待补记") -> PENDING
            outcome.startsWith("服务") -> SERVICE
            outcome.contains("失败") || outcome == "结果待核对" -> FAILED
            else -> SKIPPED
        }
    }
}

data class NotificationLogEntry(
    val timestamp: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val parsed: Boolean,
    val parsedAmount: Double? = null,
    val outcome: String = if (parsed) "已记账" else "未识别",
    val source: String = "通知读取",
    val id: String = UUID.randomUUID().toString(),
    val reason: String = "",
    val stage: String = "解析",
    val postedAt: Long? = null,
    val merchant: String? = null,
    val transactionType: TransactionType? = null,
    val paymentMethod: String? = null,
    val processingStartedAt: Long? = null,
    val processedAt: Long? = null,
    val recordedAt: Long? = null,
    val reminderRequestedAt: Long? = null,
    val reminderPostedAt: Long? = null,
    val reminderOutcome: String? = null,
) {
    val status get() = RecognitionStatus.of(outcome)
    val needsAttention get() = status in setOf(RecognitionStatus.UNRECOGNIZED, RecognitionStatus.PENDING, RecognitionStatus.FAILED) ||
        (status == RecognitionStatus.SKIPPED && reason.contains("五分钟"))
    val appLabel get() = if (packageName == SERVICE_PACKAGE) "通知服务" else RecognitionApps.find(packageName)?.label ?: packageName
    val formatted: String get() = buildString {
        append("${formatTime(timestamp)} | $outcome\n应用: $appLabel\n采集方式: $source\n阶段: $stage")
        if (reason.isNotBlank()) append("\n原因: $reason")
        append("\n包名: $packageName")
        postedAt?.let { append("\n通知发布时间: ${formatTime(it)}") }
        processingStartedAt?.let { append("\n开始处理时间: ${formatTime(it)}") }
        processedAt?.let { append("\n处理结束时间: ${formatTime(it)}") }
        recordedAt?.let { append("\n入账完成时间: ${formatTime(it)}") }
        reminderRequestedAt?.let { append("\n提醒尝试时间: ${formatTime(it)}") }
        reminderPostedAt?.let { append("\n提醒提交时间: ${formatTime(it)}") }
        reminderOutcome?.let { append("\n提醒结果: $it") }
        append("\n标题: $title\n内容: $text")
        parsedAmount?.let { append("\n金额: ${Money.format(it)}") }
        transactionType?.let { append("\n方向: ${if (it == TransactionType.INCOME) "收入" else "支出"}") }
        merchant?.let { append("\n商户: $it") }
        paymentMethod?.let { append("\n付款方式: $it") }
    }
    companion object {
        const val SERVICE_PACKAGE = "@notification-service"
        private fun formatTime(value: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(value))
    }
}

data class RecognitionApp(val id: String, val label: String, val packages: List<String>, val defaultTitle: String = "交易提醒")

object RecognitionApps {
    val apps = listOf(
        RecognitionApp("wechat", "微信", listOf("com.tencent.mm"), "微信支付"),
        RecognitionApp("alipay", "支付宝", listOf("com.eg.android.AlipayGphone", "com.eg.android.AlipayGphoneRC", "com.eg.android.AlipayGphoneGlobal", "hk.alipay.wallet")),
        RecognitionApp("jd", "京东", listOf("com.jingdong.app.mall"), "支付成功"),
        RecognitionApp("jd_finance", "京东金融", listOf("com.jd.jrapp"), "白条交易提醒"),
        RecognitionApp("meituan", "美团", listOf("com.sankuai.meituan"), "支付成功"),
        RecognitionApp("takeout", "美团外卖", listOf("com.sankuai.meituan.takeoutnew"), "支付成功"),
        RecognitionApp("dianping", "大众点评", listOf("com.dianping.v1"), "支付成功"),
        RecognitionApp("icbc", "工商银行", listOf("com.icbc")),
        RecognitionApp("boc", "中国银行", listOf("com.chinamworld.bocmbci")),
        RecognitionApp("abc", "农业银行", listOf("com.android.bankabc")),
        RecognitionApp("ccb", "建设银行", listOf("com.chinamworld.main")),
        RecognitionApp("cmb", "招商银行", listOf("cmb.pb", "com.cmbchina.ccs.pluto", "com.cmbc.ccs", "com.cmbchina.pocket")),
        RecognitionApp("bocom", "交通银行", listOf("com.bankcomm.maidanba")),
        RecognitionApp("psbc", "邮储银行", listOf("com.psbc.mobilebank")),
        RecognitionApp("dcep", "数字人民币", listOf("cn.gov.pbc.dcep")),
    )
    fun find(pkg: String) = apps.firstOrNull { pkg in it.packages }
    fun groupId(pkg: String) = find(pkg)?.id ?: pkg
}

data class RecognitionFilter(
    val applications: Set<String> = emptySet(),
    val statuses: Set<RecognitionStatus> = emptySet(),
    val channels: Set<String> = emptySet(),
    val start: Long? = null,
    val endExclusive: Long? = null,
    val attentionOnly: Boolean = false,
) : java.io.Serializable {
    fun matches(entry: NotificationLogEntry): Boolean =
        (applications.isEmpty() || RecognitionApps.groupId(entry.packageName) in applications) &&
            (statuses.isEmpty() || entry.status in statuses) &&
            (channels.isEmpty() || entry.source in channels) &&
            (start == null || entry.timestamp >= start) &&
            (endExclusive == null || entry.timestamp < endExclusive) &&
            (!attentionOnly || entry.needsAttention)
}

object RecognitionRetention {
    const val MAX_ENTRIES = 1000
    const val MAX_AGE = 7 * 24 * 60 * 60_000L
    fun keep(entries: List<NotificationLogEntry>, now: Long): List<NotificationLogEntry> =
        entries.filter { it.timestamp >= now - MAX_AGE }.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
}

data class NotificationAdmission(val reason: String? = null, val needsReview: Boolean = false)

object NotificationPreflight {
    const val MAX_DELAY_MILLIS = 24 * 60 * 60_000L

    fun check(groupSummary: Boolean, body: String, candidate: Boolean, postedAt: Long, now: Long, captureStartedAt: Long? = null): NotificationAdmission = when {
        groupSummary -> NotificationAdmission("分组汇总通知：等待单笔通知，避免重复入账")
        body.isBlank() -> NotificationAdmission("通知正文为空，无法获取交易金额")
        !candidate -> NotificationAdmission("不符合支付通知特征，仅保留接收信息")
        postedAt <= 0 || postedAt > now + 60_000L -> NotificationAdmission("通知发布时间异常，请核对后补记", true)
        captureStartedAt != null && postedAt < captureStartedAt -> NotificationAdmission("通知早于本次采集启用时间，保留待核对，不自动回放历史交易", true)
        now - postedAt > MAX_DELAY_MILLIS -> NotificationAdmission("通知延迟超过24小时，保留待核对，请核对账本后补记", true)
        else -> NotificationAdmission()
    }

    fun skipReason(groupSummary: Boolean, body: String, candidate: Boolean, postedAt: Long, now: Long): String? =
        check(groupSummary, body, candidate, postedAt, now).reason
}
