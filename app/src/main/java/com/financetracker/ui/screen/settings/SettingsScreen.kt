package com.financetracker.ui.screen.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.di.AppModule
import com.financetracker.domain.model.Banks
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.ui.component.AccountLogo
import com.financetracker.ui.component.AccountingHealthCard
import com.financetracker.ui.component.BalanceCalculator
import com.financetracker.ui.theme.CardLarge
import com.financetracker.ui.theme.HeaderGradient
import java.util.Locale

@Composable
private fun settingsModel(): SettingsViewModel {
    val appContext = LocalContext.current.applicationContext
    val factory = remember {
        SettingsViewModel.Factory(AppModule.appPreferences, AppModule.categoryRepository,
            AppModule.paymentAccountRepository, AppModule.transactionRepository, appContext)
    }
    return viewModel(factory = factory)
}

@Composable
fun SettingsScreen(onNavigateToSettings: () -> Unit = {}) {
    val vm = settingsModel()
    val accounts by vm.accounts.collectAsState()
    val feedback by vm.feedback.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PaymentAccount?>(null) }
    LaunchedEffect(feedback) { feedback?.let { snackbar.showSnackbar(it); vm.clearFeedback() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("我的钱包", style = MaterialTheme.typography.headlineMedium)
                Text("资产一眼看清，生活慢慢记好", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                val net = accounts.sumOf { it.balance }
                val assets = accounts.sumOf { it.balance.coerceAtLeast(0.0) }
                val liabilities = accounts.filter { it.balance < 0 }.sumOf { -it.balance }
                Column(Modifier.fillMaxWidth().clip(CardLarge).background(HeaderGradient).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("净资产", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("¥ ${money(net)}", style = MaterialTheme.typography.displayLarge, color = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text("${accounts.size} 个账户的当前余额", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AssetAmount("总资产", assets, Modifier.weight(1f))
                        AssetAmount("负债", liabilities, Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("账户", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { creating = true }) { Text("＋ 添加账户") }
                }
            }
            item {
                Card(shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth()) {
                        if (accounts.isEmpty()) Text("还没有账户，点击上方添加。", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        accounts.forEachIndexed { index, account ->
                            key(account.id) {
                                Row(Modifier.fillMaxWidth().clickable { editingId = account.id }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AccountLogo(account.type, account.name, size = 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(account.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    Text(money(account.balance), color = if (account.balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (index < accounts.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            item { Text("余额调整不会生成收支账单；日常收支请使用“记一笔”。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Card(onClick = onNavigateToSettings, shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("设置", style = MaterialTheme.typography.titleMedium)
                            Text("自动记账、屏幕白名单与数据导出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }
    val editing = accounts.firstOrNull { it.id == editingId }
    if (creating || editing != null) AccountEditor(
        account = editing,
        onDismiss = { creating = false; editingId = null },
        onSave = { name, type, color, balance ->
            if (editing == null) vm.createAccount(name, type, color, balance)
            else vm.updateAccount(editing.id, name, color, balance)
            creating = false; editingId = null
        },
        onDelete = editing?.let { account -> { editingId = null; deleting = account } },
    )
    deleting?.let { account ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除账户") }, text = { Text("确定删除「${account.name}」吗？已有交易记录的账户不能删除。") },
            confirmButton = { TextButton(onClick = { vm.deleteAccount(account); deleting = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

@Composable
private fun AssetAmount(label: String, amount: Double, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("¥ ${money(amount)}", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditor(account: PaymentAccount?, onDismiss: () -> Unit, onSave: (String, String, String, Double) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var type by rememberSaveable(account?.id) { mutableStateOf(account?.type ?: "bank") }
    var color by rememberSaveable(account?.id) { mutableStateOf(account?.color ?: "#B98849") }
    var showBanks by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (account == null) "添加账户" else "编辑账户", modifier = Modifier.weight(1f))
                if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (account == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("bank" to "银行卡", "wechat" to "微信", "alipay" to "支付宝", "jd" to "京东", "meituan" to "美团").forEach { (value, label) ->
                        FilterChip(selected = type == value, onClick = {
                            type = value; name = if (value == "bank") "" else label
                            color = com.financetracker.ui.theme.accountColorHex(value)
                        }, label = { Text(label) })
                    }
                }
                if (type == "bank") TextButton(onClick = { showBanks = true }) { Text("选择银行") }
                OutlinedTextField(name, { name = it }, label = { Text("账户名称") }, placeholder = { Text(if (type == "bank") "例如：招商银行 1234" else "填写账户名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                BalanceCalculator(initialBalance = account?.balance ?: 0.0, doneLabel = if (account == null) "创建账户" else "保存账户", onDone = { balance ->
                    val fallback = when (type) { "wechat" -> "微信"; "alipay" -> "支付宝"; "jd" -> "京东"; "meituan" -> "美团"; else -> "银行卡" }
                    onSave(name.trim().ifBlank { fallback }, type, color, balance)
                })
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (showBanks) AlertDialog(onDismissRequest = { showBanks = false }, title = { Text("选择银行") }, text = {
        LazyColumn(Modifier.heightIn(max = 350.dp)) {
            items(Banks.all) { bank ->
                Row(Modifier.fillMaxWidth().clickable { name = bank.name; color = bank.colorHex; showBanks = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountLogo("bank", bank.name, size = 28.dp); Spacer(Modifier.width(12.dp)); Text(bank.name)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { showBanks = false }) { Text("关闭") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingSettingsScreen(onNavigateBack: () -> Unit, onNavigateToAdd: () -> Unit) {
    val vm = settingsModel()
    val context = LocalContext.current
    val export by vm.exportState.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("设置") }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { AccountingHealthCard(onManualAdd = onNavigateToAdd) }
            item {
                Card(shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("数据导出", style = MaterialTheme.typography.titleLarge)
                        Text("导出账单为 CSV，便于自行保存与整理。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.exportCsv(context) }, enabled = export !is SettingsViewModel.ExportState.Exporting) { Text("导出 CSV") }
                        when (val state = export) {
                            SettingsViewModel.ExportState.Exporting -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            is SettingsViewModel.ExportState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("导出成功", modifier = Modifier.weight(1f))
                                TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"; putExtra(Intent.EXTRA_STREAM, state.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "分享 CSV")) }) { Text("分享") }
                                TextButton(onClick = vm::clearExportState) { Text("关闭") }
                            }
                            is SettingsViewModel.ExportState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                    }
                }
            }
            item { Text("记账助手 V${com.financetracker.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun money(value: Double) = String.format(Locale.CHINA, "%,.2f", value)
