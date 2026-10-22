package com.financetracker.domain.model

/** Names describe the stored account route; they do not infer a wallet's underlying bank card. */
object PaymentRoutes {
    private val defaults = linkedMapOf("wechat" to "微信", "alipay" to "支付宝", "bank" to "银行卡", "jd" to "京东", "meituan" to "美团")
    fun options(accounts: List<PaymentAccount>): Map<String, String> = defaults + accounts
        .filter { it.type !in defaults }.distinctBy { it.type }.associate { it.type to it.name }
}
