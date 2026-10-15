package com.financetracker.notification

import androidx.room.withTransaction
import com.financetracker.data.db.AppDatabase
import com.financetracker.data.db.entity.AccountingReceipt
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.Transaction
import com.financetracker.notification.classifier.AutoClassifier
import kotlinx.coroutines.flow.first

/** Event receipt, ledger row and balance effect commit or roll back together. */
class PaymentRecorder(private val db: AppDatabase) {
    suspend fun record(value: ParsedNotification, eventId: String, now: Long): String = db.withTransaction {
        if (db.accountingReceiptDao().exists(eventId)) return@withTransaction "重复已跳过"
        CategoryRepository(db.categoryDao()).seedIfEmpty()
        val accountsRepo = PaymentAccountRepository(db.paymentAccountDao())
        accountsRepo.seedIfEmpty()
        if (value.accountType in listOf("jd", "meituan") && db.paymentAccountDao().getByType(value.accountType) == null) {
            accountsRepo.create(if (value.accountType == "jd") "京东" else "美团", value.accountType, "#B98849", 0.0)
        }
        val accounts = db.paymentAccountDao().getAll().first().filter { it.type == value.accountType }
        val account = accounts.singleOrNull()
            ?: return@withTransaction if (accounts.isEmpty()) "待补记：请先添加账户" else "待补记：存在多个同类账户，无法确定归属"
        if (!account.isEnabled) return@withTransaction "待补记：账户已停用"
        TransactionRepository(db.transactionDao(), db.paymentAccountDao()).add(Transaction(
            amount = value.amount, type = value.transactionType,
            categoryId = AutoClassifier().classify(value.merchant, value.transactionType),
            accountId = account.id, merchant = value.merchant, date = value.payTime, source = "notification",
        ))
        db.accountingReceiptDao().insert(AccountingReceipt(eventId, now))
        db.accountingReceiptDao().prune(now - 7 * 86_400_000L)
        "已记账"
    }
}
