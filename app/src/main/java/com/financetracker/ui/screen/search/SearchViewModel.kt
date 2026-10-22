package com.financetracker.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val transactionRepo: TransactionRepository, categoryRepo: CategoryRepository, accountRepo: PaymentAccountRepository) : ViewModel() {
    data class Filters(val categoryIds: Set<Long> = emptySet(), val reimbursement: ReimbursementFilter = ReimbursementFilter.ALL, val paymentTypes: Set<String> = emptySet()) {
        val activeCount: Int get() = categoryIds.size + paymentTypes.size + if (reimbursement == ReimbursementFilter.ALL) 0 else 1
    }
    data class SearchState(val transactions: List<Transaction> = emptyList(), val loading: Boolean = false, val error: String? = null)
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _filters = MutableStateFlow(Filters())
    val filters = _filters.asStateFlow()
    private val retry = MutableStateFlow(0)
    val state: StateFlow<SearchState> = combine(_query.debounce(250), _filters, retry) { query, filters, _ -> query.trim() to filters }
        .flatMapLatest { (query, filter) ->
            transactionRepo.filter(query, filter.categoryIds, filter.reimbursement, filter.paymentTypes)
                .map { SearchState(transactions = it) }
                .onStart { emit(SearchState(loading = true)) }
                .catch { emit(SearchState(error = "暂时无法读取账单，请重试")) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchState(loading = true))
    val categories = categoryRepo.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts = accountRepo.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setQuery(query: String) { _query.value = query }
    fun applyFilters(categoryIds: Set<Long>, reimbursement: ReimbursementFilter, paymentTypes: Set<String>) { _filters.value = Filters(categoryIds, reimbursement, paymentTypes) }
    fun clearFilters() { _filters.value = Filters() }
    fun setReimbursement(filter: ReimbursementFilter) { _filters.value = _filters.value.copy(reimbursement = filter) }
    fun removeCategory(id: Long) { _filters.value = _filters.value.copy(categoryIds = _filters.value.categoryIds - id) }
    fun removePaymentType(type: String) { _filters.value = _filters.value.copy(paymentTypes = _filters.value.paymentTypes - type) }
    fun retry() { retry.value += 1 }
    class Factory(private val transactionRepo: TransactionRepository, private val categoryRepo: CategoryRepository, private val accountRepo: PaymentAccountRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(transactionRepo, categoryRepo, accountRepo) as T
    }
}
