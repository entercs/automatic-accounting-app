package com.financetracker.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.di.AppModule
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.theme.CardLarge
import com.financetracker.ui.theme.softCardShadow
import com.financetracker.ui.component.AccountLogo
import com.financetracker.ui.component.CategoryLogo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRefund: (() -> Unit)? = null,
) {
    val factory = remember {
        TransactionDetailViewModel.Factory(
            AppModule.transactionRepository,
            AppModule.categoryRepository,
            AppModule.paymentAccountRepository,
        )
    }
    val viewModel: TransactionDetailViewModel = viewModel(factory = factory)
    val transaction by viewModel.transaction.collectAsState()
    val category by viewModel.category.collectAsState()
    val account by viewModel.account.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val deleted by viewModel.deleted.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRefundDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(transactionId) { viewModel.load(transactionId) }
    LaunchedEffect(deleted) { if (deleted) onNavigateBack() }

    val t = transaction
    val currentCategory by viewModel.category.collectAsState()
    val currentAccount by viewModel.account.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("交易详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { transaction?.let { onEdit(it.id) } }) {
                        Text("编辑")
                    }
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (t == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("加载中...")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Amount
            item {
                val isExpense = t.type == TransactionType.EXPENSE
                val color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                val prefix = if (isExpense) "-" else "+"
                Card(
                    modifier = Modifier.fillMaxWidth().softCardShadow(),
                    shape = CardLarge,
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$prefix${String.format("%.2f", t.amount)}",
                            style = MaterialTheme.typography.displayLarge,
                            color = color,
                        )
                    }
                }
            }

            if (t.type == TransactionType.EXPENSE && t.reimbursable) {
                item {
                    Card(shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (t.reimbursed) "已报销" else "待报销", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("这里只更新报销状态；实际到账请另记收入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { viewModel.setReimbursed(!t.reimbursed) }) { Text(if (t.reimbursed) "改回待报销" else "标记已报销") }
                        }
                    }
                }
            }

            // Refund button (only for expenses)
            if (t.type == TransactionType.EXPENSE && onRefund != null) {
                item {
                    Button(
                        onClick = { showRefundDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardLarge,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("退款")
                    }
                }
            }

            // Account + category info
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Category row - clickable
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCategorySheet = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("分类", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryLogo(categoryName = currentCategory?.name ?: "", categoryIcon = currentCategory?.icon ?: "", size = 22.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(currentCategory?.name ?: "未知", style = MaterialTheme.typography.bodyLarge)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择分类", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        // Account row - clickable
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAccountSheet = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("账户", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AccountLogo(type = currentAccount?.type ?: "", accountName = currentAccount?.name ?: "", size = 22.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(currentAccount?.name ?: "未知", style = MaterialTheme.typography.bodyLarge)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择账户", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Merchant
            if (t.merchant.isNotBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            DetailRow("商户", t.merchant)
                        }
                    }
                }
            }

            // Note + time + source
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (t.note.isNotBlank()) {
                            DetailRow("备注", t.note)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        DetailRow("时间", detailDateFormat.format(Date(t.date)))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DetailRow("来源", if (t.source == "notification") "自动记账" else "手动记账")
                    }
                }
            }
        }
    }

    // Category selection bottom sheet
    if (showCategorySheet && t != null) {
        val filteredCategories = categories.filter {
            if (t.type == TransactionType.EXPENSE) it.type == TransactionType.EXPENSE
            else it.type == TransactionType.INCOME
        }
        SelectionBottomSheet(
            title = "选择分类",
            items = filteredCategories,
            selectedId = t.categoryId,
            onSelect = { categoryId ->
                viewModel.updateCategory(categoryId)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
            itemContent = { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateCategory(cat.id); showCategorySheet = false }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryLogo(categoryName = cat.name, categoryIcon = cat.icon, size = 28.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (cat.id == t.categoryId) {
                        Icon(Icons.Default.Check, contentDescription = "选中", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )
    }

    // Account selection bottom sheet
    if (showAccountSheet && t != null) {
        SelectionBottomSheet(
            title = "选择账户",
            items = accounts,
            selectedId = t.accountId,
            onSelect = { accountId ->
                viewModel.updateAccount(accountId)
                showAccountSheet = false
            },
            onDismiss = { showAccountSheet = false },
            itemContent = { acc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateAccount(acc.id); showAccountSheet = false }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountLogo(type = acc.type, accountName = acc.name, size = 22.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(acc.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (acc.id == t.accountId) {
                        Icon(Icons.Default.Check, contentDescription = "选中", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后将无法恢复，确定要删除这笔记录吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(); showDeleteDialog = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }

    if (showRefundDialog && t != null) {
        AlertDialog(
            onDismissRequest = { showRefundDialog = false },
            title = { Text("确认退款") },
            text = { Text("确定要退款 ${String.format("%.2f", t.amount)} 元吗？退款将返还至原账户。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.processRefund()
                    showRefundDialog = false
                    onRefund?.invoke()
                }) {
                    Text("确认退款", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefundDialog = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionBottomSheet(
    title: String,
    items: List<T>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    itemContent: @Composable (T) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            androidx.compose.foundation.lazy.LazyColumn {
                items(items) { item ->
                    itemContent(item)
                    if (items.last() != item) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private val detailDateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA)