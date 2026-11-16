package com.financetracker.notification.parser

import com.financetracker.domain.model.BankTransfer
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType
import com.financetracker.domain.model.TransferPurpose

internal object BankTransferTemplates {
    val cmbPackages = listOf("cmb.pb", "com.cmbchina.ccs.pluto", "com.cmbc.ccs", "com.cmbchina.pocket")
    private const val AMOUNT = "([0-9]+(?:\\.[0-9]{1,2})?)(?![0-9.])"
    private const val DATE = "(?:于\\s*[0-9]{1,2}月[0-9]{1,2}日\\s*[0-9]{1,2}:[0-9]{2}\\s*)?"
    private const val WALLET = "网商银行\\s*[（(]支付宝[）)]\\s*数字人民币钱包"
    private const val JD = "网银在线\\s*[-－—]\\s*京东白条"

    private val wallet = Regex("您\\s*尾号\\s*([0-9]{4})的银行卡\\s*${DATE}支出\\s*人民币\\s*${AMOUNT}\\s*元\\s*[,，。]?\\s*用于充值\\s*$WALLET")
    private val jd = Regex("您\\s*账户\\s*([0-9]{4})\\s*${DATE}在\\s*【$JD】\\s*发生快捷支付扣款\\s*[,，]?\\s*人民币\\s*$AMOUNT")
    private val walletHint = Regex("数字人民币充钱包扣款|用于充值\\s*$WALLET")
    private val jdHint = Regex("【$JD】\\s*发生快捷支付扣款")
    private val rejected = Regex("(?:充值|扣款|还款)(?:失败|处理中|未完成|已撤销)|(?:待|请)充值")

    fun looksLikeTransfer(title: String, body: String): Boolean =
        walletHint.containsMatchIn("$title $body") || jdHint.containsMatchIn(body)

    fun parse(title: String, body: String): ParsedNotification? {
        if (rejected.containsMatchIn("$title $body")) return null
        val walletMatch = wallet.find(body)
        val purpose = if (walletMatch != null) TransferPurpose.WALLET_TOP_UP else TransferPurpose.JD_REPAYMENT
        val match = walletMatch ?: jd.find(body) ?: return null
        val amount = match.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
        return ParsedNotification(
            amount, purpose.label, "bank", TransactionType.TRANSFER, System.currentTimeMillis(),
            bankTransfer = BankTransfer(match.groupValues[1], purpose),
        )
    }

    fun isConfirmation(pkg: String, title: String, body: String): Boolean {
        val text = "$title $body"
        if (rejected.containsMatchIn(text)) return false
        return when {
            pkg in setOf("com.jd.jrapp", "com.jingdong.app.mall") ->
                "白条" in text && Regex("还款(?:成功|完成|到账)|已(?:成功)?还款").containsMatchIn(text)
            pkg == "cn.gov.pbc.dcep" || pkg in AlipayParser().supportedPackages ->
                "数字人民币" in text && Regex("充值(?:成功|完成|已到账|到账|已入账|入账成功)|已(?:成功)?充值").containsMatchIn(text)
            else -> false
        }
    }
}
