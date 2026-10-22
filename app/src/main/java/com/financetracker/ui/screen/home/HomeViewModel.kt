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
import android.os.SystemClock
import com.financetracker.domain.undo.UndoWindow
import com.financetracker.domain.undo.UndoOpportunity
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val transactionRepo: TransactionRepository,
    categoryRepo: CategoryRepository,
    accountRepo: PaymentAccountRepository,
    private val undoDurationMillis: Long = 4_000L,
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

    // ====== 分页加载：交易列表 =====
    // 1.0.9 修复: 改为观察 Room Flow + 客户端分页,无障碍服务后台写入新交易时,首页列表自动刷新
    // 原来用 MutableStateFlow + loadFirstPage()/loadMore() 主动调用,后台插入记录后不会刷新
    // (用户截图:产生记录后要切到别的页面再切回来才看到新记录)
    private val _pagedSize = MutableStateFlow(PAGE_SIZE)

    val pagedTransactions: StateFlow<List<Transaction>> = _pagedSize
        .flatMapLatest { size ->
            transactionRepo.getAllTransactions().map { all -> all.take(size) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 是否有更多(分页)
    val hasMore: StateFlow<Boolean> = _pagedSize
        .flatMapLatest { size ->
            transactionRepo.getAllTransactions().map { all -> all.size > size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 正在加载（防止重复触发）
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    init {
        // 1.0.9 修复: 不再需要主动 loadFirstPage(),Flow 初始就订阅 Room
    }

    /** 重置并加载第一页（月份变更后调用,触发 pagedSize 重置） */
    fun loadFirstPage() {
        _pagedSize.value = PAGE_SIZE
    }

    /** 加载下一页（滚动到底时调用） */
    fun loadMore() {
        if (_isLoadingMore.value || !hasMore.value) return
        // 1.0.5 防御: pagedTransactions 为空时直接返回(防止 init 阶段 snapshotFlow 误触发导致 offset=0 重复加载)
        val current = pagedTransactions.value
        if (current.isEmpty()) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            _pagedSize.value = _pagedSize.value + PAGE_SIZE
            _isLoadingMore.value = false
        }
    }

    // The ViewModel owns expiry, so navigating away cannot restart the snackbar's undo window.
    private val undoWindow = UndoWindow<Transaction>(undoDurationMillis, SystemClock::elapsedRealtime)
    private val _pendingUndo = MutableStateFlow<UndoOpportunity<Transaction>?>(null)
    val pendingUndo: StateFlow<UndoOpportunity<Transaction>?> = _pendingUndo.asStateFlow()
    private var undoExpiryJob: Job? = null
    private val deletingIds = mutableSetOf<Long>()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    fun deleteTransaction(transaction: Transaction) {
        if (!deletingIds.add(transaction.id)) return
        viewModelScope.launch {
            try {
                val stored = transactionRepo.getById(transaction.id) ?: return@launch
                transactionRepo.delete(stored)
                val opportunity = undoWindow.offer(stored)
                _pendingUndo.value = opportunity
                undoExpiryJob?.cancel()
                undoExpiryJob = viewModelScope.launch {
                    delay(undoDurationMillis)
                    refreshUndo()
                }
                loadFirstPage()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _operationError.value = "删除失败，请重试" }
            finally { deletingIds.remove(transaction.id) }
        }
    }

    /** Also called on resume, to account for device sleep or a temporarily frozen process. */
    fun refreshUndo() { _pendingUndo.value = undoWindow.peek() }

    fun undoDelete(token: Long) {
        // Consume synchronously before launching IO: double taps and stale snackbars cannot restore twice.
        val transaction = undoWindow.consume(token)
        refreshUndo()
        if (transaction == null) return
        undoExpiryJob?.cancel()
        viewModelScope.launch {
            try {
                transactionRepo.add(transaction)
                loadFirstPage()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _operationError.value = "撤销失败，请核对账单并手动补记" }
        }
    }

    fun dismissUndo(token: Long) {
        if (undoWindow.dismiss(token)) undoExpiryJob?.cancel()
        refreshUndo()
    }

    fun clearOperationError(message: String) { if (_operationError.value == message) _operationError.value = null }

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
