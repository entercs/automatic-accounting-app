package com.financetracker.domain.model

data class ParsedNotification(
    val amount: Double,
    val merchant: String,
    val accountType: String,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val payTime: Long,
    val paymentMethod: String? = null,
)
