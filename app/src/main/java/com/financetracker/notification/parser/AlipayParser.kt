package com.financetracker.notification.parser

import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType

class AlipayParser : NotificationParser {
    override val supportedPackages = listOf(
        "com.eg.android.AlipayGphone",
        "com.eg.android.AlipayGphoneRC",
        "com.eg.android.AlipayGphoneGlobal",
        "hk.alipay.wallet",
    )

    // 收入关键词（注意:不再包含"红包",因为"支付红包/花呗红包"等场景下红包是支出场景,易误判）
    private val incomeKeywords = listOf("收款", "入账", "到账", "转入", "存入", "收入", "奖励", "退款", "提现到账", "收到")
    // 支出关键词
    private val expenseKeywords = listOf("支付", "付款", "消费", "转出", "提现", "还款", "转账", "免密", "自动扣款", "代扣", "支出")

    // 匹配 "12.50元" 或 "12元" 格式
    private val amountYuanPattern = Regex("(\\d+\\.?\\d*)\\s*元")
    // 匹配 "¥12.50" 或 "￥12.50" 格式（兼容旧格式）
    private val amountSymbolPattern = Regex("[¥￥](\\d+\\.?\\d*)")
    // 匹配 "人民币12.50元" 格式（银行通知）
    private val amountRmbPattern = Regex("人民币\\s*(\\d+\\.?\\d*)")
    // 匹配 "你在某某有一笔" 格式
    private val merchantInlinePattern = Regex("你在(.+?)有一笔")
    // 匹配 "收款方: xxx" 格式（兼容旧格式）
    private val merchantTagPattern = Regex("收款方[:：]\\s*(.+)")
    // 匹配 "向(.+?)付款" 或 "给(.+?)转账" 等
    private val merchantPayPattern = Regex("(?:向|给|转给)(.+?)(?:付款|转账|支付)")

    // 免密/自动扣款支付格式的金额提取
    private val autoDeductAmountPattern = Regex("一笔(\\d+\\.?\\d*)元")

    // 过滤非交易类金额的上下文关键词（需同时满足金额存在）
    private val nonTransactionAmountContexts = listOf(
        "红包今晚失效", "红包即将失效", "红包", "补贴", "补贴金额",
        "优惠券", "代金券", "积分", "加赠", "返现", "返利",
        "提额", "额度", "提升"
    )

    // 非交易类通知标题（这些通知一律不解析）
    private val nonTransactionTitles = listOf(
        "支付宝卡包", "卡包", "红包", "优惠券", "积分",
        "提额", "额度", "服务提醒", "登录提醒", "登录",
        "签约成功通知", "验证码", "活动", "营销", "推广", "广告", "公告"
    )

    override fun parse(packageName: String, title: String, text: String): ParsedNotification? {
        if (packageName !in supportedPackages) return null

        // 标题为"支付宝卡包"等非交易类通知一律跳过
        if (nonTransactionTitles.any { title.contains(it) }) {
            // "交易提醒"是合法的；但"卡包"等其他是营销/通知类
            if (title != "交易提醒" && title != "支付通知" && title != "收款通知") {
                return null
            }
        }

        val combined = "$title $text"

        // 标题整体若是"红包/优惠/额度"等纯营销类，跳过
        if (combined.contains("红包失效") || combined.contains("红包即将失效") ||
            combined.contains("提额") || combined.contains("额度提升")) {
            return null
        }

        // 提取金额
        var amount = amountYuanPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        if (amount == null) {
            amount = amountSymbolPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) {
            amount = amountRmbPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) {
            amount = autoDeductAmountPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null || amount == 0.0) return null

        // 如果金额对应的上下文包含非交易类关键词，跳过
        val match = amountYuanPattern.find(combined)
            ?: amountSymbolPattern.find(combined)
            ?: amountRmbPattern.find(combined)
            ?: autoDeductAmountPattern.find(combined)
        if (match != null) {
            val startIdx = maxOf(0, match.range.first - 12)
            val endIdx = minOf(combined.length, match.range.last + 12)
            val ctx = combined.substring(startIdx, endIdx)
            if (nonTransactionAmountContexts.any { ctx.contains(it) }) return null
        }

        // 判断收入还是支出（1.0.7 修复：优先看明确的支出信号,避免"小荷包支付红包"等"红包"误判为收入）
        val isExpense = expenseKeywords.any { combined.contains(it) }
        val isIncome = !isExpense && incomeKeywords.any { combined.contains(it) }
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // 提取商户
        val merchant = merchantInlinePattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: merchantTagPattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: merchantPayPattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: if (type == TransactionType.INCOME) "支付宝收入" else "支付宝"

        return ParsedNotification(
            amount = amount,
            merchant = merchant,
            accountType = "alipay",
            transactionType = type,
            payTime = System.currentTimeMillis(),
        )
    }
}
