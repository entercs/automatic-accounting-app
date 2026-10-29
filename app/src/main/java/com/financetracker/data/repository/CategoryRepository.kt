package com.financetracker.data.repository

import com.financetracker.data.db.dao.CategoryDao
import com.financetracker.data.db.entity.CategoryEntity
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(private val dao: CategoryDao) {

    fun getByType(type: TransactionType): Flow<List<Category>> =
        dao.getByType(type.name).map { list -> list.map { it.toDomain() } }

    fun getAll(): Flow<List<Category>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Category? =
        dao.getById(id)?.toDomain()

    suspend fun add(category: Category): Long =
        dao.insert(category.toEntity())

    suspend fun update(category: Category) =
        dao.update(category.toEntity())

    suspend fun count(): Int = dao.count()

    suspend fun automaticCategoryId(type: TransactionType): Long = dao.automaticCategoryId(type.name)

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            listOf(
                CategoryEntity(1, "餐饮日常", "🍴", "EXPENSE"),
                CategoryEntity(2, "购物消费", "🛒", "EXPENSE"),
                CategoryEntity(3, "汽车交通", "🚗", "EXPENSE"),
                CategoryEntity(4, "住房物业", "🏠", "EXPENSE"),
                CategoryEntity(5, "医疗健康", "🏥", "EXPENSE"),
                CategoryEntity(6, "娱乐休闲", "🎮", "EXPENSE"),
                CategoryEntity(7, "通讯网络", "📱", "EXPENSE"),
                CategoryEntity(8, "其他支出", "📂", "EXPENSE"),
                CategoryEntity(9, "工资收入", "💰", "INCOME"),
                CategoryEntity(10, "退款报销", "📋", "INCOME"),
                CategoryEntity(11, "其他收入", "📊", "INCOME"),
            ).forEach { dao.insert(it) }
        }
        automaticCategoryId(TransactionType.EXPENSE)
        automaticCategoryId(TransactionType.INCOME)
    }
}

private fun CategoryEntity.toDomain() = Category(id, name, icon, TransactionType.valueOf(type))
private fun Category.toEntity() = CategoryEntity(id, name, icon, type.name)
