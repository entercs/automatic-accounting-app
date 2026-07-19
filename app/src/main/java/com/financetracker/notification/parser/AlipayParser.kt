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
    // 1.0.9 新增: 匹配 "你有一笔 X 元" 这种无 "在X" 前缀的场景(花呗/积分通知常见)
    private val merchantNoPrefixPattern = Regex("你有一笔\\s*\\d+\\.?\\d*\\s*元")
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
    // 1.1.1 修复: 标题语义黑名单 — 命中其一, 不管内容里有没有"X元", 都视为非交易
    // 解决「六类毕业生可领求职补贴了」「国补上新」等政策/资讯类通知被误识别为交易
    // 注意: 不要把"通知"/"提醒"等通用词放进这里,合法交易标题也用这些(如"花呗还款提醒")
    private val nonTransactionTitles = listOf(
        "支付宝卡包", "卡包", "红包", "优惠券", "积分",
        "提额", "额度", "服务提醒", "登录提醒", "登录",
        "签约成功通知", "验证码", "活动", "营销", "推广", "广告", "公告",
        // 政策/补贴/资讯类 — 标题语义跟交易完全无关
        "补贴", "求职", "毕业生", "毕业生求职", "可领",
        "政策", "国补", "上新了", "上新",
        "资讯", "新闻",
        "中奖", "中奖了", "抽奖",
    )

    // 1.1.1 修复: 明确支出信号白名单 — 命中其一视为真实交易, 跳过"积分/优惠/补贴"等噪声上下文拒绝
    // 同时覆盖"你有一笔 X 元支出"这种通用支出格式, 修复「交易提醒 + 你有一笔2.00元的支出」漏识别
    private val definitiveExpenseSignals = listOf(
        "免密", "自动扣款", "代扣", "花呗支付", "花呗", "借呗",
        "余额宝", "余额支付", "信用支付", "银行卡支付",
        "确认收货", "订单完成", "订单支付",
        // 通用支出格式(不含"花呗/余额宝"等支付方式时也走这条)
        "你有一笔", "的支出", "已支出",
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

        // 如果金额对应的上下文包含非交易类关键词，且不属于明确交易场景，则跳过
        val match = amountYuanPattern.find(combined)
            ?: amountSymbolPattern.find(combined)
            ?: amountRmbPattern.find(combined)
            ?: autoDeductAmountPattern.find(combined)
        if (match != null) {
            val startIdx = maxOf(0, match.range.first - 12)
            val endIdx = minOf(combined.length, match.range.last + 12)
            val ctx = combined.substring(startIdx, endIdx)
            // 1.0.9 修复: 明确交易场景(免密/花呗/确认收货等) 跳过噪声上下文拒绝
            // 修复"免密/自动扣款支付 + 领取10个支付宝积分"被误拒的 bug
            val isDefinitiveExpense = definitiveExpenseSignals.any { it in combined }
            if (!isDefinitiveExpense &&
                nonTransactionAmountContexts.any { ctx.contains(it) }) return null
        }

        // 判断收入还是支出（1.0.7 修复：优先看明确的支出信号,避免"小荷包支付红包"等"红包"误判为收入）
        val isExpense = expenseKeywords.any { combined.contains(it) }
        val isIncome = !isExpense && incomeKeywords.any { combined.contains(it) }
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // 提取商户
        // 1.0.9 修复: "你有一笔 X 元" 这种无 "在X" 前缀的场景,先记录再让 default 兜底
        // 同时检查"花呗"等支付方式,让 merchant 显示"花呗"更准确
        val inlineMerchant = merchantInlinePattern.find(combined)?.groupValues?.get(1)?.trim()
        val noPrefixMatch = merchantNoPrefixPattern.containsMatchIn(combined)
        val tagMerchant = merchantTagPattern.find(combined)?.groupValues?.get(1)?.trim()
        val payMerchant = merchantPayPattern.find(combined)?.groupValues?.get(1)?.trim()
        val paymentMethodMerchant = when {
            noPrefixMatch && combined.contains("花呗") -> "花呗"
            noPrefixMatch && combined.contains("余额宝") -> "余额宝"
            else -> null
        }
        val defaultMerchant = if (type == TransactionType.INCOME) "支付宝收入" else "支付宝"
        val merchant = inlineMerchant
            ?: tagMerchant
            ?: payMerchant
            ?: paymentMethodMerchant
            ?: defaultMerchant

        return ParsedNotification(
            amount = amount,
            merchant = merchant,
            accountType = "alipay",
            transactionType = type,
            payTime = System.currentTimeMillis(),
        )
    }
}
