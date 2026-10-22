package com.financetracker.data.repository

import com.financetracker.data.db.dao.PaymentAccountDao
import com.financetracker.data.db.dao.TransactionDao
import com.financetracker.data.db.entity.TransactionEntity
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val dao: TransactionDao,
    private val accountDao: PaymentAccountDao,
) {

    fun getRecent(limit: Int = 10): Flow<List<Transaction>> =
        dao.getRecent(limit).mapToDomain()

    /** 1.0.9 新增: 全部交易 Flow(按日期倒序),无障碍服务写入新交易时自动推送 */
    fun getAllTransactions(): Flow<List<Transaction>> =
        dao.getAll().mapToDomain()

    /** 分页加载:按日期倒序,返回 offset..offset+limit 范围的交易 */
    suspend fun getByPage(limit: Int, offset: Int): List<Transaction> =
        dao.getByPage(limit, offset).map { it.toDomain() }

    /** 交易总数,用于分页结束判断 */
    suspend fun getCount(): Int = dao.count()

    fun getByMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<Transaction>> =
        dao.getByMonth(startOfMonth, endOfMonth).mapToDomain()

    fun search(query: String): Flow<List<Transaction>> =
        dao.search(query).mapToDomain()

    fun filter(query: String, categoryIds: Set<Long>, reimbursement: com.financetracker.domain.model.ReimbursementFilter, paymentTypes: Set<String> = emptySet()): Flow<List<Transaction>> =
        dao.filter(query.trim(), categoryIds.isNotEmpty(), categoryIds.toList(), reimbursement.name, paymentTypes.isNotEmpty(), paymentTypes.toList()).mapToDomain()

    suspend fun setReimbursed(id: Long, completed: Boolean) = dao.setReimbursed(id, completed)

    suspend fun getById(id: Long): Transaction? =
        dao.getById(id)?.toDomain()

    suspend fun add(transaction: Transaction): Long {
        val id = dao.insert(transaction.toEntity())
        if (transaction.type == TransactionType.TRANSFER) {
            // Transfer: source -= amount, dest += amount
            updateAccountBalance(transaction.accountId, transaction.amount, TransactionType.EXPENSE)
            transaction.transferToAccountId?.let { destId ->
                updateAccountBalance(destId, transaction.amount, TransactionType.INCOME)
            }
        } else {
            updateAccountBalance(transaction.accountId, transaction.amount, transaction.type)
        }
        return id
    }

    suspend fun update(transaction: Transaction) {
        val old = dao.getById(transaction.id) ?: return
        dao.update(transaction.toEntity())
        // Reverse old effect
        if (TransactionType.valueOf(old.type) == TransactionType.TRANSFER) {
            updateAccountBalance(old.accountId, -old.amount, TransactionType.EXPENSE)
            old.transferToAccountId?.let { updateAccountBalance(it, -old.amount, TransactionType.INCOME) }
        } else {
            updateAccountBalance(old.accountId, -old.amount, TransactionType.valueOf(old.type))
        }
        // Apply new effect
        if (transaction.type == TransactionType.TRANSFER) {
            updateAccountBalance(transaction.accountId, transaction.amount, TransactionType.EXPENSE)
            transaction.transferToAccountId?.let { updateAccountBalance(it, transaction.amount, TransactionType.INCOME) }
        } else {
            updateAccountBalance(transaction.accountId, transaction.amount, transaction.type)
        }
    }

    suspend fun delete(transaction: Transaction) {
        dao.delete(transaction.toEntity())
        if (transaction.type == TransactionType.TRANSFER) {
            // Reverse: source += amount, dest -= amount
            updateAccountBalance(transaction.accountId, transaction.amount, TransactionType.INCOME)
            transaction.transferToAccountId?.let { updateAccountBalance(it, transaction.amount, TransactionType.EXPENSE) }
        } else {
            val reversedType = if (transaction.type == TransactionType.EXPENSE) TransactionType.INCOME else TransactionType.EXPENSE
            updateAccountBalance(transaction.accountId, transaction.amount, reversedType)
        }
    }

    private suspend fun updateAccountBalance(accountId: Long, amount: Double, type: TransactionType) {
        val delta = if (type == TransactionType.EXPENSE) -amount else amount
        accountDao.adjustBalance(accountId, delta)
    }

    suspend fun getMonthlyTotal(
        type: TransactionType,
        startOfMonth: Long,
        endOfMonth: Long,
    ): Double = dao.getTotalByType(type.name, startOfMonth, endOfMonth) ?: 0.0

    suspend fun getCategoryTotal(type: TransactionType, categoryId: Long, startOfMonth: Long, endOfMonth: Long): Double =
        dao.getCategoryTotal(type.name, categoryId, startOfMonth, endOfMonth)

    suspend fun countByAccountId(accountId: Long): Int = dao.countByAccountId(accountId)

    suspend fun getDailyTotals(type: TransactionType, start: Long, end: Long): List<com.financetracker.data.db.dao.DailyTotal> =
        dao.getDailyTotals(type.name, start, end)

    private fun Flow<List<TransactionEntity>>.mapToDomain(): Flow<List<Transaction>> =
        map { list -> list.map { it.toDomain() } }
}

private fun TransactionEntity.toDomain() = Transaction(
    id = id, amount = amount, type = TransactionType.valueOf(type),
    categoryId = categoryId, accountId = accountId,
    transferToAccountId = transferToAccountId,
    merchant = merchant, note = note, reimbursable = reimbursable, reimbursed = reimbursed, date = date, source = source,
)

private fun Transaction.toEntity() = TransactionEntity(
    id = id, amount = amount, type = type.name,
    categoryId = categoryId, accountId = accountId,
    transferToAccountId = transferToAccountId,
    merchant = merchant, note = note, reimbursable = reimbursable, reimbursed = reimbursed, date = date, source = source,
)
