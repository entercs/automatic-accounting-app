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

    // 收入关键词
    private val incomeKeywords = listOf("收款", "入账", "到账", "转入", "存入", "收入", "奖励", "红包", "退款", "提现到账", "收到")
    // 支出关键词
    private val expenseKeywords = listOf("支付", "付款", "消费", "转出", "提现", "还款", "转账", "免密", "自动扣款", "代扣")

    // 匹配 "12.50元" 或 "12元" 格式
    private val amountYuanPattern = Regex("(\\d+\\.?\\d*)\\s*元")
    // 匹配 "¥12.50" 或 "￥12.50" 格式（兼容旧格式）
    private val amountSymbolPattern = Regex("[¥￥](\\d+\\.?\\d*)")
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
        "红包今晚失效", "红包即将失效", "补贴金额", "优惠券", "代金券",
        "加赠", "返现", "返利"
    )

    override fun parse(packageName: String, title: String, text: String): ParsedNotification? {
        if (packageName !in supportedPackages) return null
        val combined = "$title $text"

        // 提取金额
        var amount = amountYuanPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        if (amount == null) {
            amount = amountSymbolPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) {
            amount = autoDeductAmountPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null || amount == 0.0) return null

        // 如果金额对应的上下文包含非交易类关键词，跳过
        val match = amountYuanPattern.find(combined)
            ?: amountSymbolPattern.find(combined)
            ?: autoDeductAmountPattern.find(combined)
        if (match != null) {
            val startIdx = maxOf(0, match.range.first - 10)
            val endIdx = minOf(combined.length, match.range.last + 10)
            val ctx = combined.substring(startIdx, endIdx)
            if (nonTransactionAmountContexts.any { ctx.contains(it) }) return null
        }

        // 判断收入还是支出
        val isIncome = incomeKeywords.any { combined.contains(it) }
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
