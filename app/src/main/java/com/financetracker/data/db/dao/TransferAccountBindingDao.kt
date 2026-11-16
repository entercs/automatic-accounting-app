package com.financetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.financetracker.data.db.entity.TransferAccountBinding

@Dao
interface TransferAccountBindingDao {
    @Query("SELECT * FROM transfer_account_bindings WHERE route = :route")
    suspend fun get(route: String): TransferAccountBinding?

    @Upsert
    suspend fun save(binding: TransferAccountBinding)
}
