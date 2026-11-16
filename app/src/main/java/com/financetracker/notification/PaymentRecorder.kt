package com.financetracker.notification

import androidx.room.withTransaction
import com.financetracker.data.db.AppDatabase
import com.financetracker.data.db.entity.AccountingReceipt
import com.financetracker.data.db.entity.PaymentAccountEntity
import com.financetracker.data.db.entity.TransferAccountBinding
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.BankTransfer
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.first

/** Event receipt, ledger row and balance effect commit or roll back together. */
class PaymentRecorder(private val db: AppDatabase) {
    private val accounts = db.paymentAccountDao()
    private val bindings = db.transferAccountBindingDao()
    private val receipts = db.accountingReceiptDao()

    suspend fun record(value: ParsedNotification, eventId: String, now: Long): String = db.withTransaction {
        if (receipts.exists(eventId)) return@withTransaction "重复已跳过"
        if (value.transactionType == TransactionType.TRANSFER) {
            val route = requireNotNull(value.bankTransfer)
            val binding = bindings.get(route.bindingKey)
                ?: return@withTransaction "待补记：请确认${route.sourceLabel}和${route.purpose.destinationLabel}对应的账户"
            return@withTransaction recordTransfer(value, route, binding, eventId, now, route.purpose.label)
        }

        val categories = CategoryRepository(db.categoryDao())
        categories.seedIfEmpty()
        val accountsRepo = PaymentAccountRepository(accounts)
        accountsRepo.seedIfEmpty()
        if (value.accountType in listOf("jd", "meituan") && accounts.getByType(value.accountType) == null) {
            accountsRepo.create(if (value.accountType == "jd") "京东" else "美团", value.accountType, "#B98849", 0.0)
        }
        val candidates = accounts.getAll().first().filter { it.type == value.accountType }
        val account = candidates.singleOrNull()
            ?: return@withTransaction if (candidates.isEmpty()) "待补记：请先添加账户" else "待补记：存在多个同类账户，无法确定归属"
        if (!account.isEnabled) return@withTransaction "待补记：账户已停用"
        TransactionRepository(db).add(Transaction(
            amount = value.amount, type = value.transactionType,
            categoryId = categories.automaticCategoryId(value.transactionType),
            accountId = account.id, merchant = value.merchant, date = value.payTime, source = "notification",
            note = value.paymentMethod?.let { "付款方式：$it" }.orEmpty(),
        ))
        saveReceipt(eventId, now)
        "已记账"
    }

    suspend fun confirmedBinding(route: BankTransfer): TransferAccountBinding? = db.withTransaction {
        bindings.get(route.bindingKey)?.takeIf { binding ->
            accounts.getById(binding.fromAccountId).matches("bank") &&
                accounts.getById(binding.toAccountId).matches(route.purpose.destinationType)
        }
    }

    suspend fun confirmTransfer(
        value: ParsedNotification,
        eventId: String,
        now: Long,
        fromAccountId: Long,
        toAccountId: Long,
        rememberAccounts: Boolean,
        note: String,
    ): String = db.withTransaction {
        if (receipts.exists(eventId)) return@withTransaction "重复已跳过"
        val route = requireNotNull(value.bankTransfer)
        val binding = TransferAccountBinding(route.bindingKey, fromAccountId, toAccountId)
        val result = recordTransfer(value, route, binding, eventId, now, note)
        if (result == "已记账" && rememberAccounts) bindings.save(binding)
        result
    }

    private suspend fun recordTransfer(
        value: ParsedNotification,
        route: BankTransfer,
        binding: TransferAccountBinding,
        eventId: String,
        now: Long,
        note: String,
    ): String {
        val from = accounts.getById(binding.fromAccountId)
        val to = accounts.getById(binding.toAccountId)
        if (!from.matches("bank") || !to.matches(route.purpose.destinationType)) {
            return "待补记：转出或转入账户不可用，请重新选择"
        }
        TransactionRepository(db).add(Transaction(
            amount = value.amount, type = TransactionType.TRANSFER, categoryId = -1L,
            accountId = binding.fromAccountId, transferToAccountId = binding.toAccountId,
            merchant = value.merchant, note = note, date = value.payTime, source = "notification",
        ))
        saveReceipt(eventId, now)
        return "已记账"
    }

    private suspend fun saveReceipt(eventId: String, now: Long) {
        receipts.insert(AccountingReceipt(eventId, now))
        receipts.prune(now - RecognitionRetention.MAX_AGE)
    }

    private fun PaymentAccountEntity?.matches(type: String) = this?.isEnabled == true && this.type == type
}
