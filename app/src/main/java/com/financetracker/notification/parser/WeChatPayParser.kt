package com.financetracker.notification.parser

import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType

class WeChatPayParser : NotificationParser {
    override val supportedPackages = listOf("com.tencent.mm")

    // 收入关键词
    private val incomeKeywords = listOf("收款", "入账", "到账", "转入", "收到", "红包", "退款", "提现到账")
    // 支出关键词
    private val expenseKeywords = listOf("支付", "付款", "消费", "转出", "提现", "转账")

    // 匹配 "¥12.50" 或 "￥12.50" 格式（有/无前缀文字）
    private val amountSymbolPattern = Regex("[¥￥](\\d+\\.?\\d*)")
    // 匹配 "12.50元" 格式（无货币符号时）
    private val amountYuanPattern = Regex("(\\d+\\.?\\d*)\\s*元")
    // 匹配 "12.50" 后面紧跟"已存入"/"存入零钱"等收入到账关键词(微信红包常见格式)
    private val amountDepositedPattern = Regex("(\\d+\\.?\\d*).{0,10}?(?:已存入|存入零钱|已到账)")
    // 匹配 "收款方: xxx"
    private val merchantPattern = Regex("收款方[:：]\\s*(.+)")
    // 匹配 "向(.+?)付款" / "给(.+?)的转账"
    private val merchantPayPattern = Regex("(?:向|给)(.+?)(?:付款|收款|的转账|转账)")
    // 匹配 "来自(.+?)的红包" (微信红包通知: 来自 XX 的红包)
    private val merchantRedPacketPattern = Regex("来自(.+?)的红包")

    override fun parse(packageName: String, title: String, text: String): ParsedNotification? {
        if (packageName != "com.tencent.mm") return null

        // Require payment context: 必须有支付/收款/红包/转账等关键词,过滤掉普通聊天消息
        // 1.0.5 修复: 加上"红包"和"收到"以支持微信红包通知
        val paymentKeywords = listOf("支付", "付款", "收款", "账单", "扣款", "消费", "交易", "提现", "退款", "红包", "收到", "到账", "入账", "零钱")
        val hasPaymentContext = title == "微信支付" || title == "微信红包"
                || title.contains("支付") || title.contains("付款") || title.contains("收款")
                || title.contains("红包") || title.contains("转账")
                || paymentKeywords.any { it in text }
        if (!hasPaymentContext) return null

        val combined = "$title $text"

        var amount = PaymentAmount.find(combined) ?: amountSymbolPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        if (amount == null) {
            amount = amountYuanPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) {
            // 1.0.5 修复: 微信红包通知金额可能只在"已存入"前显示,无"元"或"¥"
            amount = amountDepositedPattern.find(combined)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        if (amount == null) return null

        // 判断收入还是支出
        val isIncome = Regex("收款到账|收款成功|收到.*(?:转账|收款)|退款成功|退款到账|已存入零钱|入账|转入|到账通知").containsMatchIn(combined) || title == "收款通知"
        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

        // 提取商户: 红包通知的发送人来自"来自XX的红包",其他用原有 pattern
        val merchant = merchantRedPacketPattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: merchantPattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: merchantPayPattern.find(combined)?.groupValues?.get(1)?.trim()
            ?: if (combined.contains("红包")) "微信红包"
            else if (type == TransactionType.INCOME) "微信收入"
            else "微信"

        return ParsedNotification(
            amount = amount,
            merchant = merchant,
            accountType = "wechat",
            transactionType = type,
            payTime = System.currentTimeMillis(),
        )
    }
}
