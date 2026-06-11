package com.financetracker.notification.parser

import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType

class JDPayParser : NotificationParser {
    override val supportedPackages = listOf("com.jingdong.app.mall")

    // 收入关键词
    private val incomeKeywords = listOf("收款", "入账", "到账", "退款", "返")
    // 支出关键词
    private val expenseKeywords = listOf("支付", "消费", "购买")

    // 匹配支付/交易金额（带¥或元）
    private val amountSymbolPattern = Regex("[¥￥](\\d+\\.?\\d*)")
    private val amountYuanPattern = Regex("(\\d+\\.?\\d*)\\s*元")

    // 过滤掉优惠/礼金/会员省钱类金额的关键词
    private val excludeKeywords = listOf("礼金", "优惠", "抵扣", "红包", "叠加", "满减", "立减", "返现", "积分", "加赠", "券", "返利", "抵", "京豆",
        // 1.0.8 修复: 排除京东 PLUS 会员省钱金额等营销内容(如"PLUS 会员省钱金额已达 621.86 元"被误识别为消费)
        "PLUS", "省钱金额", "省回", "会费", "权益", "会员")

    // 非交易类通知标题（营销/额度/账户类），一律不解析
    private val nonTransactionTitles = listOf(
        "白条额度变动提醒", "白条额度变动", "白条额度", "额度变动",
        "额度提升", "额度调整", "提额", "优惠券", "京豆",
        "服务通知", "账户通知", "评价提醒", "评价", "晒单", "签到",
        "活动", "营销", "推广", "广告", "公告"
    )

    override fun parse(packageName: String, title: String, text: String): ParsedNotification? {
        if (packageName != "com.jingdong.app.mall") return null
        val combined = "$title $text"

        // 非交易类标题直接跳过
        if (nonTransactionTitles.any { title.contains(it) }) return null

        // 整体包含额度提升/提额等营销关键词，跳过
        if (combined.contains("额度提升") || combined.contains("额度已提升") ||
            combined.contains("提额") || combined.contains("白条额度")) {
            return null
        }

        // 提取所有金额
        val amounts = mutableListOf<Double>()

        amountSymbolPattern.findAll(combined).forEach { match ->
            match.groupValues[1].toDoubleOrNull()?.let { amount ->
                // 检查这个金额是否在排除关键词附近
                val startIdx = match.range.first
                val endIdx = match.range.last + 1
                val context = combined.substring(maxOf(0, startIdx - 6), minOf(combined.length, endIdx + 6))
                val shouldExclude = excludeKeywords.any { context.contains(it) }
                if (!shouldExclude) amounts.add(amount)
            }
        }

        amountYuanPattern.findAll(combined).forEach { match ->
            match.groupValues[1].toDoubleOrNull()?.let { amount ->
                // 检查这个金额是否在排除关键词附近
                val startIdx = match.range.first
                val endIdx = match.range.last + 1
                val context = combined.substring(maxOf(0, startIdx - 6), minOf(combined.length, endIdx + 6))
                val shouldExclude = excludeKeywords.any { context.contains(it) }
                if (!shouldExclude) amounts.add(amount)
            }
        }

        // 优先取最大金额（通常是实际交易金额）
        val amount = amounts.maxOrNull() ?: return null

        // 判断收入还是支出
        val isIncome = incomeKeywords.any { combined.contains(it) }
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        return ParsedNotification(
            amount = amount,
            merchant = if (type == TransactionType.INCOME) "京东收入" else "京东",
            accountType = "jd",
            transactionType = type,
            payTime = System.currentTimeMillis(),
        )
    }
}
