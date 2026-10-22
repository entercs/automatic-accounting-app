package com.financetracker.ui.screen.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.di.AppModule
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.ReimbursementFilter
import com.financetracker.domain.model.PaymentRoutes
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.component.*
import com.financetracker.ui.theme.CardLarge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onTransactionClick: (Long) -> Unit = {}, onNavigateBack: () -> Unit = {}) {
    val factory = remember { SearchViewModel.Factory(AppModule.transactionRepository, AppModule.categoryRepository, AppModule.paymentAccountRepository) }
    val vm: SearchViewModel = viewModel(factory = factory)
    val query by vm.query.collectAsState()
    val filters by vm.filters.collectAsState()
    val state by vm.state.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val paymentOptions = remember(accounts) { PaymentRoutes.options(accounts) }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val groups = remember(state.transactions) { groupTransactionsByDay(state.transactions) }
    val rows = remember(groups) { groups.flatMap { listOf<Any>(it) + it.transactions } }
    var showFilters by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var resetQuery by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("查账") }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CapsuleSearchField(query, vm::setQuery, Modifier.weight(1f), onSearch = { keyboard?.hide(); focus.clearFocus() })
                FilledTonalIconButton(onClick = { keyboard?.hide(); focus.clearFocus(); showFilters = true }) {
                    BadgedBox(badge = { if (filters.activeCount > 0) Badge { Text("${filters.activeCount}") } }) { Icon(Icons.Default.FilterAlt, "筛选账单") }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReimbursementFilter.entries) { value ->
                    FilterChip(selected = filters.reimbursement == value, onClick = { vm.setReimbursement(value) }, label = { Text(value.label) })
                }
                items(filters.categoryIds.sorted(), key = { it }) { id ->
                    InputChip(selected = true, onClick = { vm.removeCategory(id) }, label = { Text(categoryMap[id]?.name ?: "分类 $id") }, trailingIcon = { Icon(Icons.Default.Close, "移除分类", Modifier.size(14.dp)) })
                }
                items(filters.paymentTypes.sorted(), key = { "payment_$it" }) { type ->
                    InputChip(selected = true, onClick = { vm.removePaymentType(type) }, label = { Text("支付：${paymentOptions[type] ?: "其他"}") }, trailingIcon = { Icon(Icons.Default.Close, "移除支付途径", Modifier.size(14.dp)) })
                }
                if (filters.activeCount > 0) item { TextButton(onClick = { resetQuery = false; showReset = true }) { Text("清除筛选") } }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.loading) item {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.error != null) item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!); TextButton(onClick = vm::retry) { Text("重试") }
                }
            } else {
                item {
                    val expense = state.transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    val income = state.transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    Card(shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("共 ${state.transactions.size} 笔${if (filters.activeCount > 0 || query.isNotBlank()) "符合条件的账单" else "账单"}", style = MaterialTheme.typography.titleMedium)
                            if (filters.reimbursement != ReimbursementFilter.ALL) {
                                Text("${filters.reimbursement.label}合计 ¥ ${money(expense)}", color = MaterialTheme.colorScheme.primary)
                            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text("支出 ¥ ${money(expense)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                Text("收入 ¥ ${money(income)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (rows.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Search, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (filters.activeCount > 0 || query.isNotBlank()) "没有符合条件的账单" else "还没有账单", style = MaterialTheme.typography.titleMedium)
                        Text("可调整关键词、分类、支付途径或报销状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (filters.activeCount > 0 || query.isNotBlank()) TextButton(onClick = { resetQuery = true; showReset = true }) { Text("重置查找") }
                    }
                }
                items(rows, key = { row -> when (row) {
                    is DailyGroup -> "day_${row.transactions.first().id}"
                    is Transaction -> "tx_${row.id}"
                    else -> error("Unexpected search row")
                } }) { row ->
                    when (row) {
                        is DailyGroup -> DateHeader(row.dateLabel, row.totalExpense, row.totalIncome)
                        is Transaction -> TransactionItem(row, categoryMap[row.categoryId], accountMap[row.accountId], row.transferToAccountId?.let { accountMap[it] }, onClick = {
                            keyboard?.hide(); focus.clearFocus(); onTransactionClick(row.id)
                        })
                    }
                }
            }
        }
    }
    if (showFilters) FilterSheet(categories, paymentOptions, filters, onDismiss = { showFilters = false }, onApply = { ids, status, types -> vm.applyFilters(ids, status, types); showFilters = false })
    if (showReset) ResetFiltersDialog(clearQuery = resetQuery, onDismiss = { showReset = false }, onConfirm = {
        if (resetQuery) vm.setQuery("")
        vm.clearFilters(); showReset = false
    })
}

@Composable
private fun CapsuleSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, onSearch: () -> Unit) {
    Surface(modifier.height(44.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
                modifier = Modifier.weight(1f).semantics { contentDescription = "搜索账单" },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { input -> Box { if (query.isEmpty()) Text("商户、金额、账户或备注", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis); input() } })
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "清除搜索", Modifier.size(18.dp)) }
            else Spacer(Modifier.width(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(categories: List<Category>, paymentOptions: Map<String, String>, current: SearchViewModel.Filters, onDismiss: () -> Unit, onApply: (Set<Long>, ReimbursementFilter, Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(current.categoryIds) }
    var reimbursement by remember { mutableStateOf(current.reimbursement) }
    var paymentTypes by remember { mutableStateOf(current.paymentTypes) }
    var confirmReset by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("筛选账单", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "取消筛选") }
                }
                Text("分类、支付途径可多选，并与关键词和报销状态组合查找", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("报销状态", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReimbursementFilter.entries.forEach { value -> FilterChip(selected = reimbursement == value, onClick = { reimbursement = value }, label = { Text(value.label) }) }
                }
                Text("支付途径", style = MaterialTheme.typography.titleMedium)
                Text("按账单账户匹配，转账可匹配转出或转入途径。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = paymentTypes.isEmpty(), onClick = { paymentTypes = emptySet() }, label = { Text("全部途径") })
                    paymentOptions.forEach { (type, name) ->
                        FilterChip(selected = type in paymentTypes, onClick = { paymentTypes = if (type in paymentTypes) paymentTypes - type else paymentTypes + type }, label = { Text(name) })
                    }
                }
                Text("分类", style = MaterialTheme.typography.titleMedium)
                FilterChip(selected = selected.isEmpty(), onClick = { selected = emptySet() }, label = { Text("全部分类") })
                listOf(TransactionType.EXPENSE to "支出分类", TransactionType.INCOME to "收入分类").forEach { (type, label) ->
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.filter { it.type == type }.forEach { category ->
                            FilterChip(selected = category.id in selected, onClick = { selected = if (category.id in selected) selected - category.id else selected + category.id }, label = { Text(category.name) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.weight(1f)) { Text("重置") }
                Button(onClick = { onApply(selected, reimbursement, paymentTypes) }, modifier = Modifier.weight(2f)) { Text("应用筛选") }
            }
        }
    }
    if (confirmReset) ResetFiltersDialog(onDismiss = { confirmReset = false }, onConfirm = {
        confirmReset = false
        onApply(emptySet(), ReimbursementFilter.ALL, emptySet())
    })
}

@Composable
private fun ResetFiltersDialog(clearQuery: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (clearQuery) "重置查找？" else "重置筛选？") },
        text = { Text(if (clearQuery) "将清除搜索关键词、分类、支付途径和报销状态，并立即应用。" else "将清除分类、支付途径和报销状态筛选，保留搜索关键词，并立即应用。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认重置并应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun money(value: Double) = String.format(Locale.CHINA, "%,.2f", value)
