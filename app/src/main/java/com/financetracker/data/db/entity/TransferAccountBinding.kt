package com.financetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_account_bindings")
data class TransferAccountBinding(
    @PrimaryKey val route: String,
    val fromAccountId: Long,
    val toAccountId: Long,
)
