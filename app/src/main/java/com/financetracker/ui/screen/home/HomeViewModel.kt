package com.financetracker.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.domain.model.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val transactionRepo: TransactionRepository,
    categoryRepo: CategoryRepository,
    accountRepo: PaymentAccountRepository,
) : ViewModel() {

    data class MonthRange(val start: Long, val end: Long)

    private fun currentMonthRange(): MonthRange {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        return MonthRange(start, end)
    }

    private val _monthRange = MutableStateFlow(currentMonthRange())
    val monthRange: StateFlow<MonthRange> = _monthRange.asStateFlow()

    init {
        // 周期检查月份变更：跨月时自动重置支出/结余
        viewModelScope.launch {
            while (true) {
                delay(60_000L) // 每分钟检查一次
                val newRange = currentMonthRange()
                if (newRange != _monthRange.value) {
                    _monthRange.value = newRange
                }
            }
        }
    }

    /** 供 UI 主动调用（如屏幕恢复时）以立即刷新当月范围 */
    fun refreshMonthRange() {
        val newRange = currentMonthRange()
        if (newRange != _monthRange.value) {
            _monthRange.value = newRange
        }
    }

    val recentTransactions: StateFlow<List<Transaction>> =
        transactionRepo.getRecent(20).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyTransactions: StateFlow<List<Transaction>> = _monthRange
        .flatMapLatest { range -> transactionRepo.getByMonth(range.start, range.end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> =
        categoryRepo.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<PaymentAccount>> =
        accountRepo.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deletedTransaction = MutableStateFlow<Transaction?>(null)
    val deletedTransaction: StateFlow<Transaction?> = _deletedTransaction.asStateFlow()

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepo.delete(transaction)
            _deletedTransaction.value = transaction
        }
    }

    fun undoDelete() {
        val transaction = _deletedTransaction.value ?: return
        viewModelScope.launch {
            transactionRepo.add(transaction.copy(id = 0))
            _deletedTransaction.value = null
        }
    }

    fun clearUndo() {
        _deletedTransaction.value = null
    }

    class Factory(
        private val transactionRepo: TransactionRepository,
        private val categoryRepo: CategoryRepository,
        private val accountRepo: PaymentAccountRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(transactionRepo, categoryRepo, accountRepo) as T
        }
    }
}
