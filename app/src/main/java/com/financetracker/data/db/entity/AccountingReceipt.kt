package com.financetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persist event identity alongside the transaction, so reconnects/process restarts cannot replay it. */
@Entity(tableName = "accounting_receipts")
data class AccountingReceipt(@PrimaryKey val eventId: String, val createdAt: Long)
