package com.financetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.financetracker.data.db.entity.AccountingReceipt

@Dao
interface AccountingReceiptDao {
    @Query("SELECT EXISTS(SELECT 1 FROM accounting_receipts WHERE eventId = :id)")
    suspend fun exists(id: String): Boolean

    @Insert
    suspend fun insert(receipt: AccountingReceipt)

    @Query("DELETE FROM accounting_receipts WHERE createdAt < :before")
    suspend fun prune(before: Long)
}
