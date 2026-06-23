package com.financetracker.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.di.AppModule
import com.financetracker.ui.component.DateHeader
import com.financetracker.ui.component.TransactionItem
import com.financetracker.ui.component.groupTransactionsByDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onTransactionClick: (Long) -> Unit = {}) {
    val factory = remember {
        SearchViewModel.Factory(
            AppModule.transactionRepository,
            AppModule.categoryRepository,
            AppModule.paymentAccountRepository,
        )
    }
    val viewModel: SearchViewModel = viewModel(factory = factory)

    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val dailyGroups = remember(results) { groupTransactionsByDay(results) }
    val flatItems = remember(dailyGroups) {
        dailyGroups.flatMap { group -> listOf<Any>(group) + group.transactions }
    }

    Scaffold(
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("搜索商户/金额/账户...") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
            }

            if (results.isEmpty() && query.isNotBlank()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("未找到相关记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(flatItems.size, key = { index ->
                val item = flatItems[index]
                when (item) {
                    is com.financetracker.ui.component.DailyGroup -> "h_${item.dateLabel}"
                    is com.financetracker.domain.model.Transaction -> "tx_${item.id}"
                    else -> "u"
                }
            }) { index ->
                when (val item = flatItems[index]) {
                    is com.financetracker.ui.component.DailyGroup -> {
                        DateHeader(item.dateLabel, item.totalExpense, item.totalIncome)
                    }
                    is com.financetracker.domain.model.Transaction -> {
                        TransactionItem(
                            transaction = item,
                            category = categoryMap[item.categoryId],
                            account = accountMap[item.accountId],
                            toAccount = item.transferToAccountId?.let { accountMap[it] },
                            onClick = { onTransactionClick(item.id) },
                        )
                    }
                }
            }
        }
    }
}
