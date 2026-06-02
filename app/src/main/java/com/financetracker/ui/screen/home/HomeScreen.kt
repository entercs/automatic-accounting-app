package com.financetracker.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.di.AppModule
import com.financetracker.ui.component.DateHeader
import com.financetracker.ui.component.TransactionItem
import com.financetracker.ui.component.groupTransactionsByDay
import com.financetracker.ui.theme.Green500
import com.financetracker.ui.theme.Red500
import com.financetracker.ui.theme.AccountIconDisplay
import com.financetracker.ui.theme.accountColor
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onTransactionClick: (Long) -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }

    val factory = remember {
        HomeViewModel.Factory(
            AppModule.transactionRepository,
            AppModule.categoryRepository,
            AppModule.paymentAccountRepository,
        )
    }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val monthlyTransactions by viewModel.monthlyTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val displayedTransactions = remember(recentTransactions, searchQuery) {
        if (searchQuery.isBlank()) recentTransactions
        else recentTransactions.filter { tx ->
            tx.merchant.contains(searchQuery, ignoreCase = true) ||
            tx.note.contains(searchQuery, ignoreCase = true) ||
            "%.2f".format(tx.amount).contains(searchQuery) ||
            tx.amount.toBigDecimal().stripTrailingZeros().toPlainString().contains(searchQuery)
        }
    }
    val dailyGroups = remember(displayedTransactions) { groupTransactionsByDay(displayedTransactions) }
    val flatItems = remember(dailyGroups) {
        dailyGroups.flatMap { group -> listOf<Any>(group) + group.transactions }
    }

    val totalExpense = remember(monthlyTransactions) {
        monthlyTransactions
            .filter { it.type == com.financetracker.domain.model.TransactionType.EXPENSE }
            .sumOf { it.amount }
    }
    val totalIncome = remember(monthlyTransactions) {
        monthlyTransactions
            .filter { it.type == com.financetracker.domain.model.TransactionType.INCOME }
            .sumOf { it.amount }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var revealedTransactionId by remember { mutableStateOf<Long?>(null) }

    // 屏幕恢复时主动刷新当月范围，确保跨月时支出/结余立刻重置
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMonthRange()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Undo snackbar — auto-dismiss after ~4 seconds
    LaunchedEffect(deletedTransaction?.id) {
        val tx = deletedTransaction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "已删除「${tx.merchant.ifBlank { tx.note.ifBlank { "记录" } }}」",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        } else {
            viewModel.clearUndo()
        }
    }


    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = Green500,
                contentColor = Color.White,
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "记账")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        // Initially scroll past the search bar (item 0) to hide it
        LaunchedEffect(Unit) {
            listState.scrollToItem(1, 0)
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Search bar — initially scrolled off-screen above, pull down to reveal
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索…", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }

            // Summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        val balance = totalIncome - totalExpense
                        val balanceColor = if (balance >= 0) Green500 else Red500
                        Text("结余", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${String.format("%.2f", balance)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SummaryItem("支出", totalExpense, Red500)
                            SummaryItem("收入", totalIncome, Green500)
                        }
                    }
                }
            }

            item {
                Text("最近交易", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }

            if (displayedTransactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("还没有交易记录\n点击右下角按钮开始记账", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(flatItems.size, key = { index ->
                val item = flatItems[index]
                when (item) {
                    is com.financetracker.ui.component.DailyGroup -> "h_${item.dateLabel}"
                    is com.financetracker.domain.model.Transaction -> "tx_${item.id}"
                    else -> "unknown"
                }
            }) { index ->
                when (val item = flatItems[index]) {
                    is com.financetracker.ui.component.DailyGroup -> {
                        DateHeader(item.dateLabel, item.totalExpense, item.totalIncome)
                    }
                    is com.financetracker.domain.model.Transaction -> {
                        SwipeRevealItem(
                            transaction = item,
                            isRevealed = revealedTransactionId == item.id,
                            onReveal = { revealedTransactionId = item.id },
                            onHide = { revealedTransactionId = null },
                            onDelete = { viewModel.deleteTransaction(item) },
                        ) {
                            TransactionItem(
                                transaction = item,
                                category = categoryMap[item.categoryId],
                                account = accountMap[item.accountId],
                                toAccount = item.transferToAccountId?.let { accountMap[it] },
                                onClick = {
                                    if (revealedTransactionId == item.id) {
                                        revealedTransactionId = null
                                    } else {
                                        onTransactionClick(item.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${String.format("%.2f", amount)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

private fun monthLabel(): String {
    val cal = Calendar.getInstance()
    return "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月"
}

@Composable
private fun SwipeRevealItem(
    transaction: com.financetracker.domain.model.Transaction,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    // Animate to preset position when reveal state changes
    LaunchedEffect(isRevealed) {
        if (isRevealed) offsetX.animateTo(-200f, tween(200))
        else offsetX.animateTo(0f, tween(200))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        // Red delete background — full height, on the RIGHT
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Red500),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconButton(onClick = { onDelete(); onHide() }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // Foreground — finger tracks slightly, then snaps to reveal or hide on release
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(transaction.id) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            if (dragTotal < -40f) onReveal() else onHide()
                        },
                        onDragCancel = { onHide() },
                        onHorizontalDrag = { _, amount ->
                            dragTotal += amount
                            val next = (offsetX.value + amount).coerceIn(-210f, 0f)
                            scope.launch { offsetX.snapTo(next) }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
