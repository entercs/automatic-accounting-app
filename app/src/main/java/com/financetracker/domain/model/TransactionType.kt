package com.financetracker.domain.model

enum class TransactionType(val label: String) {
    INCOME("收入"), EXPENSE("支出"), TRANSFER("转账"),
}
