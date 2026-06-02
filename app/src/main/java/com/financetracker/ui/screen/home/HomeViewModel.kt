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

    companion object {
        private const val PAGE_SIZE = 20
    }

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

    // ====== 分页加载：交易列表 ======
    // 累计已加载的首页交易
    private val _pagedTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val pagedTransactions: StateFlow<List<Transaction>> = _pagedTransactions.asStateFlow()

    // 是否有更多
    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // 正在加载（防止重复触发）
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // 数据变化信号（删除/添加后递增,触发重置）
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    init {
        // 首次加载第一页
        loadFirstPage()
    }

    /** 重置并加载第一页（删除/添加/月份变更后调用） */
    fun loadFirstPage() {
        viewModelScope.launch {
            _pagedTransactions.value = transactionRepo.getByPage(PAGE_SIZE, 0)
            _hasMore.value = _pagedTransactions.value.size >= PAGE_SIZE &&
                transactionRepo.getCount() > PAGE_SIZE
        }
    }

    /** 加载下一页（滚动到底时调用） */
    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            val offset = _pagedTransactions.value.size
            val newPage = transactionRepo.getByPage(PAGE_SIZE, offset)
            if (newPage.isEmpty()) {
                _hasMore.value = false
            } else {
                _pagedTransactions.value = _pagedTransactions.value + newPage
                _hasMore.value = newPage.size >= PAGE_SIZE
            }
            _isLoadingMore.value = false
        }
    }

    // ====== 删除/撤销 ======
    private val _deletedTransaction = MutableStateFlow<Transaction?>(null)
    val deletedTransaction: StateFlow<Transaction?> = _deletedTransaction.asStateFlow()

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepo.delete(transaction)
            _deletedTransaction.value = transaction
            // 触发分页重载
            _dataVersion.value = _dataVersion.value + 1
            loadFirstPage()
        }
    }

    fun undoDelete() {
        val transaction = _deletedTransaction.value ?: return
        viewModelScope.launch {
            transactionRepo.add(transaction.copy(id = 0))
            _deletedTransaction.value = null
            // 触发分页重载
            _dataVersion.value = _dataVersion.value + 1
            loadFirstPage()
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
