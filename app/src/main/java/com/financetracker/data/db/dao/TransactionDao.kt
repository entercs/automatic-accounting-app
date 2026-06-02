package com.financetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.financetracker.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int = 10): Flow<List<TransactionEntity>>

    /**
     * 分页查询:按日期倒序,跳过前 offset 条,取 limit 条
     * offset 从 0 开始
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getByPage(limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions WHERE date >= :startOfMonth AND date < :endOfMonth ORDER BY date DESC")
    fun getByMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE (merchant LIKE '%' || :query || '%'
            OR note LIKE '%' || :query || '%'
            OR CAST(amount AS TEXT) LIKE '%' || :query || '%'
            OR accountId IN (SELECT id FROM payment_accounts WHERE name LIKE '%' || :query || '%'))
        ORDER BY date DESC
    """)
    fun search(query: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE type = :type AND date >= :startOfMonth AND date < :endOfMonth
    """)
    suspend fun getTotalByType(type: String, startOfMonth: Long, endOfMonth: Long): Double?

    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = :type AND categoryId = :categoryId
        AND date >= :startOfMonth AND date < :endOfMonth
    """)
    suspend fun getCategoryTotal(type: String, categoryId: Long, startOfMonth: Long, endOfMonth: Long): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countByAccountId(accountId: Long): Int

    @Query("""
        SELECT (date + 28800000) / 86400000 * 86400000 - 28800000 as dayStart, SUM(amount) as total
        FROM transactions
        WHERE type = :type AND date >= :start AND date < :end
        GROUP BY (date + 28800000) / 86400000 ORDER BY dayStart
    """)
    suspend fun getDailyTotals(type: String, start: Long, end: Long): List<DailyTotal>
}

data class DailyTotal(
    val dayStart: Long,
    val total: Double,
)
