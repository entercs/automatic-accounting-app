package com.financetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.financetracker.data.db.entity.PaymentAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAccountDao {
    @Insert
    suspend fun insert(account: PaymentAccountEntity): Long

    @Update
    suspend fun update(account: PaymentAccountEntity)

    @Query("SELECT * FROM payment_accounts ORDER BY id ASC")
    fun getAll(): Flow<List<PaymentAccountEntity>>

    @Query("SELECT * FROM payment_accounts WHERE isEnabled = 1 ORDER BY id ASC")
    fun getEnabled(): Flow<List<PaymentAccountEntity>>

    @Query("SELECT * FROM payment_accounts WHERE id = :id")
    suspend fun getById(id: Long): PaymentAccountEntity?

    @Query("SELECT * FROM payment_accounts WHERE type = :type LIMIT 1")
    suspend fun getByType(type: String): PaymentAccountEntity?

    @Query("SELECT COUNT(*) FROM payment_accounts")
    suspend fun count(): Int

    @Query("UPDATE payment_accounts SET balance = :balance WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Double)

    @Query("UPDATE payment_accounts SET balance = balance + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: Double)

    @Query("UPDATE payment_accounts SET name = :name, color = :color WHERE id = :id")
    suspend fun updateNameAndColor(id: Long, name: String, color: String)

    @Query("UPDATE payment_accounts SET name = :name, color = :color, balance = :balance WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, color: String, balance: Double)

    @Query("DELETE FROM payment_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
