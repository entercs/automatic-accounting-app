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

    // 1.0.9 修复: 营销主题关键词(标题命中) — 单独看上下文够不到
    // 原 excludeKeywords 只在金额 6 字符窗口里查,但 PLUS/会员 通常在 title, 240元 在 text, 隔几十字符, 窗口命中不了
    // 现在: 标题含这些营销主题时, 必须同时有明确支付动作才算交易, 否则跳过
    // 修复"您的PLUS会员还有7天到期"被误识别为消费支出
    private val marketingTopicKeywords = listOf(
        "PLUS", "省钱金额", "省回", "会费", "权益", "会员",
        "续费", "到期", "即将到期", "免费兑换",
    )

    // 1.0.9 修复: 营销文本短语(全文本匹配) — 出现即视为非交易
    // 例如"续费继续享全年无限免邮,购物折上95折,还有价值240元生活服务免费兑换,开..."里的 PLUS 营销
    // 1.0.9 修订: 区分普通字符串(用 Regex.escape 转义)和正则(直接编译)
    //   避免有人误加特殊字符导致意外行为,也避免每次 parse 重新编译
    private val marketingTextRegexes: List<Regex> = listOf(
        "续费继续享", "续费可享", "免费兑换", "无限免邮", "享全年",
        "省钱金额已达", "省回金额", "购物折上",
    ).map { Regex(Regex.escape(it)) } +
        // 正则:享X无限(续费/营销常见)
        listOf(Regex("享.*无限"))

    // 支付动作关键词(标题含营销主题时,必须同时命中其一才视为真实交易)
    // 1.0.9 修复: 加"开通成功/续费成功/续费完成" — 合法"开通/续费"通知应记录为支出
    private val paymentActionKeywords = listOf(
        "支付", "付款", "扣款", "到账", "入账", "收款", "购买", "消费", "充值",
        "支付成功", "付款成功", "扣款成功", "支付完成",
        "开通成功", "续费成功", "续费完成", "开通完成",
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

        // 1.0.9 修复: 营销文本短语全文本匹配 — 出现即跳过
        // 修复"PLUS 续费提醒"含"240元生活服务免费兑换"被误识别为消费支出
        // 1.0.9 优化: 预编译正则(字面值用 Regex.escape 转义,正则直接编译)
        if (marketingTextRegexes.any { it.containsMatchIn(combined) }) return null

        // 1.0.9 修复: 标题含营销主题词时, 必须同时有支付动作才视为真实交易
        // 例: 标题"您的PLUS会员还有7天到期！" + 内容"还有价值240元生活服务免费兑换" → 跳过
        //     标题"京东PLUS会员扣款成功" + 内容"支付240元" → 正常记录
        if (marketingTopicKeywords.any { it in title }) {
            val hasPaymentAction = paymentActionKeywords.any { it in combined }
            if (!hasPaymentAction) return null
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
