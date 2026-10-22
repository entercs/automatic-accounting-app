package com.financetracker.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financetracker.data.db.entity.TransactionEntity
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionDetailViewModel(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: PaymentAccountRepository,
) : ViewModel() {

    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _transaction.asStateFlow()

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _account = MutableStateFlow<PaymentAccount?>(null)
    val account: StateFlow<PaymentAccount?> = _account.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _accounts = MutableStateFlow<List<PaymentAccount>>(emptyList())
    val accounts: StateFlow<List<PaymentAccount>> = _accounts.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun load(transactionId: Long) {
        viewModelScope.launch {
            val t = transactionRepo.getById(transactionId)
            _transaction.value = t
            if (t != null) {
                _category.value = categoryRepo.getById(t.categoryId)
                _account.value = accountRepo.getById(t.accountId)
            }
        }
        viewModelScope.launch {
            categoryRepo.getAll().collect { _categories.value = it }
        }
        viewModelScope.launch {
            accountRepo.getAll().collect { _accounts.value = it }
        }
    }

    fun updateCategory(categoryId: Long) {
        val t = _transaction.value ?: return
        val updated = t.copy(categoryId = categoryId)
        viewModelScope.launch {
            transactionRepo.update(updated)
            _transaction.value = updated
            _category.value = categoryRepo.getById(categoryId)
        }
    }

    fun updateAccount(accountId: Long) {
        val t = _transaction.value ?: return
        val updated = t.copy(accountId = accountId)
        viewModelScope.launch {
            transactionRepo.update(updated)
            _transaction.value = updated
            _account.value = accountRepo.getById(accountId)
        }
    }

    fun setReimbursed(completed: Boolean) {
        val t = _transaction.value ?: return
        if (t.type != TransactionType.EXPENSE || !t.reimbursable) return
        viewModelScope.launch {
            transactionRepo.setReimbursed(t.id, completed)
            _transaction.value = transactionRepo.getById(t.id)
        }
    }

    fun delete() {
        val t = _transaction.value ?: return
        viewModelScope.launch {
            transactionRepo.delete(t)
            _deleted.value = true
        }
    }

    fun processRefund() {
        val t = _transaction.value ?: return
        if (t.type != TransactionType.EXPENSE) return
        viewModelScope.launch {
            val refundTransaction = Transaction(
                amount = t.amount,
                type = TransactionType.INCOME,
                categoryId = 10, // 退款报销
                accountId = t.accountId,
                merchant = t.merchant,
                note = "退款：${t.note}",
                date = System.currentTimeMillis(),
                source = "manual",
            )
            transactionRepo.add(refundTransaction)
        }
    }

    class Factory(
        private val transactionRepo: TransactionRepository,
        private val categoryRepo: CategoryRepository,
        private val accountRepo: PaymentAccountRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionDetailViewModel(transactionRepo, categoryRepo, accountRepo) as T
        }
    }
}
