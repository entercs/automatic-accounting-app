package com.financetracker.domain.model

enum class ReimbursementFilter(val label: String) {
    ALL("全部"), PENDING("待报销"), REIMBURSED("已报销")
}

val Transaction.reimbursementLabel: String
    get() = when {
        type != TransactionType.EXPENSE || !reimbursable -> "无需报销"
        reimbursed -> "已报销"
        else -> "待报销"
    }
