package com.financetracker.ui.screen.recognition

import com.financetracker.domain.calculator.Money
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.di.AppModule
import com.financetracker.domain.model.PaymentRoutes
import com.financetracker.notification.NotificationLogger
import com.financetracker.notification.RecognitionApps
import com.financetracker.notification.parser.PaymentParsers
import com.financetracker.ui.theme.CardLarge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationTestScreen(sampleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("recognition_test", Context.MODE_PRIVATE) }
    val sample = remember(sampleId) { NotificationLogger.getAll().find { it.id == sampleId } }
    val initialPackage = sample?.packageName ?: preferences.getString("last_package", "com.eg.android.AlipayGphone").orEmpty()
    var pkg by rememberSaveable(sampleId) { mutableStateOf(initialPackage) }
    var title by rememberSaveable(sampleId) { mutableStateOf(sample?.title ?: RecognitionApps.find(initialPackage)?.defaultTitle.orEmpty()) }
    var body by rememberSaveable(sampleId) { mutableStateOf(sample?.text.orEmpty()) }
    var custom by rememberSaveable(sampleId) { mutableStateOf(RecognitionApps.find(initialPackage) == null) }
    var expanded by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ParsedNotification?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val route = result?.bankTransfer
    var boundAccounts by remember(result) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(result) {
        val binding = route?.let { AppModule.paymentRecorder.confirmedBinding(it) }
        if (binding != null) {
            val from = AppModule.paymentAccountRepository.getById(binding.fromAccountId)
            val to = AppModule.paymentAccountRepository.getById(binding.toAccountId)
            if (from != null && to != null) boundAccounts = from.name to to.name
        }
    }
    val app = RecognitionApps.find(pkg.trim())
    fun resetResult() { result = null; feedback = null }
    Scaffold(topBar = { TopAppBar(title = { Text("测试通知") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("输入一条通知，看看能否正确识别。", style = MaterialTheme.typography.titleMedium)
            Text("测试仅预览金额、方向和商户，不写入账本或识别记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sampleId.isNotBlank()) Text(if (sample != null) "已从识别记录带入，可修改后再测试" else "原记录已清理，请重新输入样本", style = MaterialTheme.typography.bodySmall)
            ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(value = if (custom) "自定义来源" else app?.label ?: "选择应用", onValueChange = {}, readOnly = true,
                    label = { Text("应用来源") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 340.dp)) {
                    RecognitionApps.apps.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                            pkg = option.packages.first(); title = option.defaultTitle; custom = false; expanded = false; resetResult()
                            preferences.edit().putString("last_package", pkg).apply()
                        })
                    }
                    DropdownMenuItem(text = { Text("自定义来源 / 输入包名") }, onClick = { custom = true; expanded = false; resetResult() })
                }
            }
            if (custom) {
                OutlinedTextField(pkg, { pkg = it; resetResult() }, label = { Text("来源应用包名") }, singleLine = true,
                    supportingText = { Text(app?.let { "匹配应用：${it.label}" } ?: "可输入包名，尚未适配的来源会明确提示") }, modifier = Modifier.fillMaxWidth())
            } else {
                Text("包名：$pkg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { custom = true }) { Text("编辑包名") }
            }
            OutlinedTextField(title, { title = it; resetResult() }, label = { Text("通知标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val titles = when (app?.id) {
                    "wechat" -> listOf("微信支付", "收款通知", "微信红包")
                    "alipay" -> listOf("交易提醒", "支付通知", "收款通知")
                    "cmb" -> listOf("数字人民币充钱包扣款", "快捷支付扣款")
                    else -> listOf("交易提醒", "支付成功", "退款成功")
                }
                titles.forEach { suggestion -> SuggestionChip(onClick = { title = suggestion; resetResult() }, label = { Text(suggestion) }) }
            }
            OutlinedTextField(body, { body = it; resetResult() }, label = { Text("通知正文") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
            Button(enabled = pkg.isNotBlank() && title.isNotBlank() && body.isNotBlank(), onClick = {
                preferences.edit().putString("last_package", pkg.trim()).apply()
                result = PaymentParsers.parse(pkg.trim(), title, body)
                feedback = if (result == null) PaymentParsers.failureReason(pkg.trim(), title, body) else "识别成功"
            }, modifier = Modifier.fillMaxWidth()) { Text("测试识别") }
            feedback?.let { text ->
                Card(Modifier.fillMaxWidth(), shape = CardLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text, style = MaterialTheme.typography.titleMedium, color = if (result != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        result?.let { value ->
                            Text("${value.transactionType.label} ¥${Money.format(value.amount)}", style = MaterialTheme.typography.headlineMedium)
                            val transfer = value.bankTransfer
                            if (transfer != null) {
                                Text("用途：${transfer.purpose.label}")
                                Text("转出账户：${boundAccounts?.first ?: transfer.sourceLabel}")
                                Text("转入账户：${boundAccounts?.second ?: transfer.purpose.destinationLabel}")
                                if (boundAccounts == null) Text("待确认账本账户")
                            } else {
                                Text("商户：${value.merchant}")
                                Text("付款方式：${value.paymentMethod ?: "通知未提供"}")
                                Text("归属途径：${PaymentRoutes.options(emptyList())[value.accountType] ?: value.accountType}")
                            }
                        }
                        Text("这只是解析预览；真实自动记账还需要接收通知并匹配可用账户。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
