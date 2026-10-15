package com.financetracker.ui.screen.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financetracker.data.repository.StatisticsRepository
import com.financetracker.data.repository.StatisticsRepository.CategorySummary
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.TransactionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class StatPeriod { WEEK, MONTH, YEAR, CUSTOM }

class StatisticsViewModel(
    private val statisticsRepo: StatisticsRepository,
    private val transactionRepo: TransactionRepository,
) : ViewModel() {

    // Date range (millis)
    private val _startDate = MutableStateFlow(0L)
    val startDate: StateFlow<Long> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow(0L)
    val endDate: StateFlow<Long> = _endDate.asStateFlow()

    // Current period
    private val _period = MutableStateFlow(StatPeriod.MONTH)
    val period: StateFlow<StatPeriod> = _period.asStateFlow()

    // Navigation offset (for WEEK/MONTH/YEAR)
    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    // Data
    private val _expenseTotal = MutableStateFlow(0.0)
    val expenseTotal: StateFlow<Double> = _expenseTotal.asStateFlow()

    private val _incomeTotal = MutableStateFlow(0.0)
    val incomeTotal: StateFlow<Double> = _incomeTotal.asStateFlow()

    private val _expenseCategories = MutableStateFlow<List<CategorySummary>>(emptyList())
    val expenseCategories: StateFlow<List<CategorySummary>> = _expenseCategories.asStateFlow()

    private val _incomeCategories = MutableStateFlow<List<CategorySummary>>(emptyList())
    val incomeCategories: StateFlow<List<CategorySummary>> = _incomeCategories.asStateFlow()

    // Daily trend data
    private val _expenseDailyTotals = MutableStateFlow<List<StatisticsRepository.DailyTotal>>(emptyList())
    val expenseDailyTotals: StateFlow<List<StatisticsRepository.DailyTotal>> = _expenseDailyTotals.asStateFlow()

    private val _incomeDailyTotals = MutableStateFlow<List<StatisticsRepository.DailyTotal>>(emptyList())
    val incomeDailyTotals: StateFlow<List<StatisticsRepository.DailyTotal>> = _incomeDailyTotals.asStateFlow()

    // Selected type: null = overview, EXPENSE = expense detail, INCOME = income detail
    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    val selectedType: StateFlow<TransactionType?> = _selectedType.asStateFlow()

    // Comparison data
    private val _prevExpenseTotal = MutableStateFlow(0.0)
    val prevExpenseTotal: StateFlow<Double> = _prevExpenseTotal.asStateFlow()

    private val _prevIncomeTotal = MutableStateFlow(0.0)
    val prevIncomeTotal: StateFlow<Double> = _prevIncomeTotal.asStateFlow()

    // Yearly data
    private val _monthlyExpenses = MutableStateFlow<List<Double>>(emptyList())
    val monthlyExpenses: StateFlow<List<Double>> = _monthlyExpenses.asStateFlow()

    private val _yearlyTotalExpense = MutableStateFlow(0.0)
    val yearlyTotalExpense: StateFlow<Double> = _yearlyTotalExpense.asStateFlow()

    // Header display
    private val _headerText = MutableStateFlow("")
    val headerText: StateFlow<String> = _headerText.asStateFlow()

    // Can go forward? (future disallowed)
    private val _canGoNext = MutableStateFlow(false)
    val canGoNext: StateFlow<Boolean> = _canGoNext.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _transactions = MutableStateFlow<List<com.financetracker.domain.model.Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var observeJob: Job? = null

    init {
        _selectedType.value = TransactionType.EXPENSE
        setPeriod(StatPeriod.MONTH)
    }

    fun setPeriod(newPeriod: StatPeriod) {
        _period.value = newPeriod
        _offset.value = 0
        setCustomRange()
        startObserving()
    }

    fun setCustomStartEnd(start: Long, end: Long) {
        if (start > end || start > System.currentTimeMillis()) return
        _period.value = StatPeriod.CUSTOM
        _startDate.value = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        _endDate.value = Calendar.getInstance().apply { timeInMillis = minOf(end, System.currentTimeMillis()); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
        _canGoNext.value = false
        updateHeader()
        startObserving()
    }

    fun previous() {
        if (_period.value == StatPeriod.CUSTOM) return
        _offset.value -= 1
        setCustomRange()
        startObserving()
    }

    fun next() {
        if (!_canGoNext.value) return
        _offset.value += 1
        setCustomRange()
        startObserving()
    }

    private fun setCustomRange() {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()

        when (_period.value) {
            StatPeriod.WEEK -> {
                // Current week: Monday to Sunday
                cal.timeInMillis = now
                cal.add(Calendar.WEEK_OF_YEAR, _offset.value)
                val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                _startDate.value = cal.timeInMillis
                val weekStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                _endDate.value = cal.timeInMillis
                // Can go next if this week ends before today
                _canGoNext.value = weekStart + 7L * 86400000 <= now
            }
            StatPeriod.MONTH -> {
                cal.timeInMillis = now
                cal.add(Calendar.MONTH, _offset.value)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                _startDate.value = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                _endDate.value = cal.timeInMillis
                // Check if next month exists
                _canGoNext.value = _offset.value < 0
            }
            StatPeriod.YEAR -> {
                cal.timeInMillis = now
                cal.add(Calendar.YEAR, _offset.value)
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                _startDate.value = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                cal.add(Calendar.MILLISECOND, -1)
                _endDate.value = cal.timeInMillis
                _canGoNext.value = minOf(cal.timeInMillis, now) < now
            }
            StatPeriod.CUSTOM -> { /* Set externally */ }
        }
        updateHeader()
    }

    private fun updateHeader() {
        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.CHINA)
        _headerText.value = "${sdf.format(java.util.Date(_startDate.value))} - ${sdf.format(java.util.Date(_endDate.value))}"
    }

    private fun startObserving() {
        observeJob?.cancel()
        val start = _startDate.value
        val end = _endDate.value
        observeJob = viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                transactionRepo.getByMonth(start, end + 1).collect {
                    _transactions.value = it
                    loadData(start, end + 1)
                    _isRefreshing.value = false
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "暂时无法加载统计，请重试" }
            finally { _isRefreshing.value = false }
        }
    }

    fun toggleType(type: TransactionType) {
        _selectedType.value = if (_selectedType.value == type) null else type
    }

    fun selectType(type: TransactionType?) { _selectedType.value = type }

    fun refresh() { startObserving() }

    private suspend fun loadData(start: Long, end: Long) {
        _expenseTotal.value = statisticsRepo.getMonthlyExpenseTotal(start, end)
        _incomeTotal.value = statisticsRepo.getMonthlyIncomeTotal(start, end)
        _expenseCategories.value = statisticsRepo.getCategorySummaries(start, end, TransactionType.EXPENSE)
        _incomeCategories.value = statisticsRepo.getCategorySummaries(start, end, TransactionType.INCOME)
        _expenseDailyTotals.value = statisticsRepo.getDailyTotals(start, end, TransactionType.EXPENSE)
        _incomeDailyTotals.value = statisticsRepo.getDailyTotals(start, end, TransactionType.INCOME)

        // Previous period
        val duration = end - start
        val previous = Calendar.getInstance().apply { timeInMillis = start }
        when (_period.value) {
            StatPeriod.MONTH -> previous.add(Calendar.MONTH, -1)
            StatPeriod.YEAR -> previous.add(Calendar.YEAR, -1)
            StatPeriod.WEEK -> previous.add(Calendar.DAY_OF_MONTH, -7)
            StatPeriod.CUSTOM -> previous.timeInMillis = start - duration
        }
        val prevStart = previous.timeInMillis
        val prevEnd = start
        _prevExpenseTotal.value = statisticsRepo.getMonthlyExpenseTotal(prevStart, prevEnd)
        _prevIncomeTotal.value = statisticsRepo.getMonthlyIncomeTotal(prevStart, prevEnd)

        // Yearly breakdown
        if (_period.value == StatPeriod.YEAR) {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = start
            val yearExpenses = mutableListOf<Double>()
            var totalY = 0.0
            for (month in 0..11) {
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
                val ms = cal.timeInMillis
                cal.add(java.util.Calendar.MONTH, 1)
                val me = cal.timeInMillis
                val mTotal = statisticsRepo.getMonthlyExpenseTotal(ms, minOf(me, System.currentTimeMillis()))
                yearExpenses.add(mTotal)
                totalY += mTotal
            }
            _monthlyExpenses.value = yearExpenses
            _yearlyTotalExpense.value = totalY
        }
    }

    class Factory(
        private val statisticsRepo: StatisticsRepository,
        private val transactionRepo: TransactionRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatisticsViewModel(statisticsRepo, transactionRepo) as T
        }
    }
}
