package com.financetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.financetracker.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY id ASC")
    fun getByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND type = :type ORDER BY id ASC LIMIT 1")
    suspend fun findByNameAndType(name: String, type: String): CategoryEntity?

    @Transaction
    suspend fun automaticCategoryId(type: String): Long {
        require(type == "EXPENSE" || type == "INCOME")
        return findByNameAndType("自动记账", type)?.id
            ?: insert(CategoryEntity(name = "自动记账", icon = "auto_bookkeeping", type = type))
    }

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}
