package com.financetracker.notification.parser

import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType

/** One registry for live capture and the sample tester. Only allowlisted apps enter the pipeline. */
object PaymentParsers {
    val parsers: List<NotificationParser> = listOf(WeChatPayParser(), AlipayParser(), JDPayParser(), BankCardParser(), MeituanPayParser())
    val packages: List<String> = parsers.flatMap { it.supportedPackages }.distinct()
    fun supports(pkg: String) = pkg in packages

    fun isCandidate(pkg: String, title: String, text: String): Boolean {
        if (!supports(pkg)) return false
        if (pkg == "com.tencent.mm") return Regex("微信支付|微信收款|收款到账|收款通知|支付凭证|微信红包|转账到账").containsMatchIn(title)
        return Regex("交易|支付|扣款|入账|收款|退款|到账|支出|消费|还款").containsMatchIn("$title $text")
    }

    fun parse(pkg: String, title: String, text: String): ParsedNotification? {
        if (!isCandidate(pkg, title, text)) return null
        val parser = parsers.firstOrNull { pkg in it.supportedPackages } ?: return null
        val cleanTitle = normalize(title)
        val cleanText = normalize(text)
        val all = "$cleanTitle $cleanText"
        if (Regex("美元|USD|港币|HKD|欧元|EUR|日元|JPY", RegexOption.IGNORE_CASE).containsMatchIn(all)) return null
        if (Regex("支付失败|付款失败|交易失败|扣款失败|退款失败|待支付|待付款|等待付款|待领取|还款提醒|验证码|退款申请|退款处理中|到账处理中").containsMatchIn(all)) return null
        // Chat messages mentioning amounts are not authoritative WeChat payment notifications.
        if (pkg == "com.tencent.mm" && !Regex("微信支付|微信收款|收款到账|收款通知|支付凭证|微信红包|转账到账").containsMatchIn(cleanTitle)) return null
        if (pkg == "com.tencent.mm" && "红包" in all && !Regex("已存入|已领取|领取成功|已到账").containsMatchIn(all)) return null
        val result = parser.parse(pkg, cleanTitle, cleanText) ?: return null
        if (!result.amount.isFinite() || result.amount <= 0) return null
        return result
    }

    private fun normalize(value: String): String = value.replace('，', ',').replace(Regex("(?<=\\d),(?=\\d{3}(?:\\D|$))"), "")
        .replace('\u00a0', ' ')
}

/** Prefer an explicitly labelled transaction amount over balances, coupons or order totals. */
object PaymentAmount {
    private const val NUMBER = "([0-9]+(?:\\.[0-9]{1,2})?)"
    private val labelled = Regex("(?:实付(?:金额)?|实际支付|支付金额|交易金额|付款金额|扣款金额|消费金额|收款金额|入账金额|退款金额|支出金额)[：:\\s]*(?:人民币|RMB|CNY)?[¥￥\\s]*$NUMBER")
    private val action = Regex("(?:消费|支出|扣款|入账|收入|收款|付款|支付|到账|退款)(?:成功)?[：:\\s]*(?:人民币|RMB|CNY)?[¥￥\\s]*$NUMBER\\s*(?:元|[¥￥])?")
    private val actual = Regex("(?:实付(?:金额)?|实际支付)[：:\\s]*(?:人民币|RMB|CNY)?[¥￥\\s]*$NUMBER")
    private val refund = Regex("退款金额[：:\\s]*(?:人民币|RMB|CNY)?[¥￥\\s]*$NUMBER")
    private val generic = Regex("你有一笔[¥￥\\s]*$NUMBER\\s*元(?:的)?(?:支出|收入)")
    fun find(text: String): Double? {
        val match = (if ("退款" in text) refund.find(text) else null) ?: actual.find(text) ?: labelled.find(text) ?: generic.find(text) ?: action.find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }
    }
}

class MeituanPayParser : NotificationParser {
    override val supportedPackages = listOf("com.sankuai.meituan", "com.sankuai.meituan.takeoutnew", "com.dianping.v1")
    override fun parse(packageName: String, title: String, text: String): ParsedNotification? {
        if (packageName !in supportedPackages) return null
        val all = "$title $text"
        val refund = Regex("退款成功|退款到账|退款已到账").containsMatchIn(all)
        if (!refund && !Regex("支付成功|付款成功|扣款成功|已支付|支付完成").containsMatchIn(all)) return null
        val amount = PaymentAmount.find(all) ?: Regex("[¥￥]([0-9]+(?:\\.[0-9]{1,2})?)").find(all)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val merchant = Regex("(?:商家|商户|收款方)[：:]\\s*([^，,。;；\\n]+)").find(text)?.groupValues?.get(1)?.trim() ?: "美团"
        return ParsedNotification(amount, merchant, "meituan", if (refund) TransactionType.INCOME else TransactionType.EXPENSE, System.currentTimeMillis())
    }
}
