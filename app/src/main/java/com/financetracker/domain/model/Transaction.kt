package com.financetracker.domain.model

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val accountId: Long,
    val transferToAccountId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val reimbursable: Boolean = false,
    val reimbursed: Boolean = false,
    val date: Long,
    val source: String = "manual",
)
