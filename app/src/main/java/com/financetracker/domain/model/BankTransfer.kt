package com.financetracker.domain.model

enum class TransferPurpose(val label: String, val destinationType: String, val destinationLabel: String) {
    WALLET_TOP_UP("数字人民币钱包充值", "dcep", "数字人民币钱包"),
    JD_REPAYMENT("白条还款", "jd", "京东"),
}

data class BankTransfer(val cardTail: String, val purpose: TransferPurpose) {
    val bindingKey get() = "cmb:$cardTail:${purpose.name}"
    val sourceLabel get() = "招行尾号 $cardTail"
}
