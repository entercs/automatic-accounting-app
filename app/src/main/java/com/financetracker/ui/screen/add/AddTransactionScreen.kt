package com.financetracker.ui.screen.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.di.AppModule
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.component.CalculatorKeypad
import com.financetracker.ui.component.SegmentedTab
import com.financetracker.ui.component.VoiceInputButton
import com.financetracker.ui.theme.CardLarge
import com.financetracker.ui.theme.softCardShadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(editTransactionId: Long? = null, refundTransactionId: Long? = null, onNavigateBack: () -> Unit) {
    val factory = remember {
        AddTransactionViewModel.Factory(
            AppModule.transactionRepository,
            AppModule.categoryRepository,
            AppModule.paymentAccountRepository,
            AppModule.appPreferences,
        )
    }
    val viewModel: AddTransactionViewModel = viewModel(factory = factory)

    LaunchedEffect(editTransactionId) {
        editTransactionId?.let { viewModel.loadTransaction(it) }
    }
    LaunchedEffect(refundTransactionId) {
        refundTransactionId?.let { viewModel.loadRefund(it) }
    }

    val amount by viewModel.amount.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val transferToAccountId by viewModel.transferToAccountId.collectAsState()
    val reimbursable by viewModel.reimbursable.collectAsState()
    val merchant by viewModel.merchant.collectAsState()
    val note by viewModel.note.collectAsState()
    val date by viewModel.date.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val showEquals by viewModel.showEquals.collectAsState()

    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date)

    LaunchedEffect(saved) {
        if (saved) onNavigateBack()
    }

    val categories = if (selectedType == TransactionType.EXPENSE) expenseCategories else incomeCategories
    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()

    // Account picker dialog
    if (showAccountPicker) {
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = { Text("选择支付账户") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        AccountChip(account, isSelected = selectedAccountId == account.id) {
                            viewModel.setAccountId(account.id)
                            showAccountPicker = false
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccountPicker = false }) { Text("关闭") }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Amount
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { viewModel.setAmount(it) },
                            label = { Text("金额") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            singleLine = true,
                            readOnly = true,
                                                    )
                        VoiceInputButton { (amt, _, merch) ->
                            amt?.let { a -> viewModel.setAmount(a.toBigDecimal().stripTrailingZeros().toPlainString()) }
                            merch?.let { m -> viewModel.setMerchant(m) }
                        }
                    }
                }

                // 1.0.9 UI 规范: 类型切换 Tab - 用 SegmentedTab 替代卡片色块(禁止色块高亮)
                item {
                    SegmentedTab(
                        options = listOf(
                            "支出" to TransactionType.EXPENSE,
                            "收入" to TransactionType.INCOME,
                            "转账" to TransactionType.TRANSFER,
                        ),
                        selected = selectedType,
                        onSelect = { viewModel.setType(it) },
                    )
                }

                // Reimbursable toggle (right after type, only for expense)
                if (selectedType == TransactionType.EXPENSE) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.setReimbursable(!reimbursable) }.softCardShadow(),
                            shape = CardLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = if (reimbursable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                            border = if (reimbursable) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("可报销", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = reimbursable, onCheckedChange = { viewModel.setReimbursable(it) })
                            }
                        }
                    }
                }

                // Transfer account selectors (only for transfer)
                if (selectedType == TransactionType.TRANSFER) {
                    item {
                        Text("转出账户", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { account ->
                                AccountChip(account, isSelected = selectedAccountId == account.id) {
                                    viewModel.setAccountId(account.id)
                                }
                            }
                        }
                    }
                    item {
                        Text("转入账户", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.filter { it.id != selectedAccountId }.forEach { account ->
                                AccountChip(account, isSelected = transferToAccountId == account.id) {
                                    viewModel.setTransferToAccountId(account.id)
                                }
                            }
                        }
                    }
                }

                // Category selector (for non-transfer)
                if (selectedType != TransactionType.TRANSFER) {
                    item {
                        Text("选择分类", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { cat ->
                                CategoryChip(cat, isSelected = selectedCategoryId == cat.id) {
                                    viewModel.setCategoryId(cat.id)
                                }
                            }
                        }
                    }
                }

                // Date
                item {
                    Text("交易时间", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }.softCardShadow(),
                        shape = CardLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Text(
                            text = dateFormat.format(Date(date)),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                // Merchant
                item {
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = viewModel::setMerchant,
                        label = { Text("商户名称（选填）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                // Note
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = viewModel::setNote,
                        label = { Text("备注（选填）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                // Note templates
                item {
                    val templates = listOf("午餐", "晚餐", "通勤", "咖啡", "超市", "房租", "话费", "快递")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        templates.forEach { tpl ->
                            TextButton(onClick = { viewModel.setNote(tpl) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Text(tpl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // Calculator keypad with account indicator
            CalculatorKeypad(
                showEquals = showEquals,
                selectedAccount = if (selectedType != TransactionType.TRANSFER) selectedAccount else null,
                onAccountClick = { showAccountPicker = true },
                onKey = { key -> viewModel.onKeypadKey(key) },
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.setDate(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun CategoryChip(category: Category, isSelected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bgColor = if (isSelected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isSelected) BorderStroke(1.5.dp, primary) else null,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.financetracker.ui.component.CategoryLogo(
                categoryName = category.name,
                categoryIcon = category.icon,
                size = 20.dp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AccountChip(account: PaymentAccount, isSelected: Boolean, onClick: () -> Unit) {
    val accColor = try { Color(android.graphics.Color.parseColor(account.color)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
    val bgColor = if (isSelected) accColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isSelected) accColor else null
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.financetracker.ui.component.AccountLogo(type = account.type, accountName = account.name, size = 20.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                account.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) accColor else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)
