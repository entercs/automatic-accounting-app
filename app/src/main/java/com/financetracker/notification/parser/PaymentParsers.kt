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
        if (pkg == "com.jd.jrapp" && "白条" !in "$title $text") return false
        return Regex("交易|支付|扣款|入账|收款|退款|到账|支出|消费|还款").containsMatchIn("$title $text")
    }

    fun failureReason(pkg: String, title: String, text: String): String {
        return rejectionReason(pkg, normalize(title), normalize(text))
            ?: "未匹配到有效的已完成交易金额或收支模板，可带入测试或手动核对"
    }

    private fun rejectionReason(pkg: String, title: String, text: String): String? {
        if (!supports(pkg)) return "尚未适配此来源应用"
        if (!isCandidate(pkg, title, text)) return "标题或正文不符合该应用的支付通知特征"
        val all = "$title $text"
        if (Regex("美元|USD|港币|HKD|欧元|EUR|日元|JPY", RegexOption.IGNORE_CASE).containsMatchIn(all)) return "外币交易暂不自动记账，请核对币种后补记"
        if (Regex("支付失败|付款失败|交易失败|扣款失败|退款失败|待支付|待付款|等待付款|验证码|退款申请|退款处理中|到账处理中").containsMatchIn(all)) return "通知包含失败、待支付或处理中的状态，未视为已完成交易"
        val confirmedAlipay = pkg in AlipayParser().supportedPackages && title.trim() == "交易提醒" && AlipayEvidence.completed(text)
        if (!confirmedAlipay && Regex("待领取|还款提醒").containsMatchIn(all)) return "通知为待领取或还款提醒，未视为已完成交易"
        if (pkg == "com.tencent.mm" && "红包" in all && !Regex("已存入|已领取|领取成功|已到账").containsMatchIn(all)) return "微信红包尚无领取或到账凭据"
        return null
    }

    fun parse(pkg: String, title: String, text: String): ParsedNotification? {
        val cleanTitle = normalize(title)
        val cleanText = normalize(text)
        if (rejectionReason(pkg, cleanTitle, cleanText) != null) return null
        val parser = parsers.firstOrNull { pkg in it.supportedPackages } ?: return null
        val result = parser.parse(pkg, cleanTitle, cleanText) ?: return null
        if (!result.amount.isFinite() || result.amount <= 0) return null
        return result.copy(merchant = MerchantText.clean(result.merchant))
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

/** A trusted Alipay title still needs an actual transaction phrase, rather than a coupon amount. */
object AlipayEvidence {
    private val generic = Regex("你(?:在.+?)?有一笔[¥￥\\s]*[0-9]+(?:\\.[0-9]{1,2})?\\s*元(?:的)?(支出|收入|免密|自动扣款|代扣)")
    fun direction(text: String): TransactionType? = generic.find(text)?.groupValues?.get(1)?.let {
        if (it == "收入") TransactionType.INCOME else TransactionType.EXPENSE
    }
    fun completed(text: String) = generic.containsMatchIn(text) ||
        (Regex("支付成功|付款成功|扣款成功|退款成功|退款到账|收款到账|已入账").containsMatchIn(text) && PaymentAmount.find(text) != null)
}
