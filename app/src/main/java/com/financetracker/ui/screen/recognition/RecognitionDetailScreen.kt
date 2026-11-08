package com.financetracker.ui.screen.recognition

import com.financetracker.domain.calculator.Money
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.financetracker.notification.*
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.theme.CardLarge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionDetailScreen(id: String, onBack: () -> Unit, onTest: (String) -> Unit, onManualAdd: (String) -> Unit) {
    val logs by NotificationLogger.logs.collectAsState()
    val entry = logs.find { it.id == id }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("识别详情") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (entry == null) {
                Text("这条记录已清理", style = MaterialTheme.typography.titleLarge)
                Text("识别记录有保留期限和条数上限，清理不会删除账单。")
            } else {
                Card(Modifier.fillMaxWidth(), shape = CardLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.status.label, style = MaterialTheme.typography.headlineSmall,
                            color = if (entry.needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        if (entry.reason.isBlank() && entry.outcome != entry.status.label) Text(entry.outcome, style = MaterialTheme.typography.bodyLarge)
                        if (entry.parsedAmount != null) Text("¥ ${Money.format(entry.parsedAmount)}", style = MaterialTheme.typography.headlineLarge)
                        Text(entry.reason.ifBlank { "历史版本未记录详细原因，可用原文重新测试。" }, style = MaterialTheme.typography.bodyMedium)
                        Text("处理阶段：${entry.stage}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                DetailLine("应用来源", entry.appLabel)
                DetailLine("采集方式", entry.source)
                DetailLine("接收时间", recordTime(entry.timestamp, "yyyy-MM-dd HH:mm:ss"))
                entry.postedAt?.let { DetailLine("通知发布时间", recordTime(it, "yyyy-MM-dd HH:mm:ss")) }
                entry.postedAt?.let {
                    val delaySeconds = ((entry.timestamp - it).coerceAtLeast(0) / 1000)
                    DetailLine("通知接收延迟", if (delaySeconds < 60) "${delaySeconds}秒" else "${delaySeconds / 60}分${delaySeconds % 60}秒")
                }
                entry.processingStartedAt?.let { DetailLine("开始处理时间", recordTime(it, "yyyy-MM-dd HH:mm:ss")) }
                entry.recordedAt?.let { DetailLine("入账完成时间", recordTime(it, "yyyy-MM-dd HH:mm:ss")) }
                entry.processedAt?.let { DetailLine("处理结束时间", recordTime(it, "yyyy-MM-dd HH:mm:ss")) }
                entry.reminderRequestedAt?.let { DetailLine("提醒尝试时间", recordTime(it, "yyyy-MM-dd HH:mm:ss")) }
                entry.reminderPostedAt?.let { DetailLine("提醒提交时间", recordTime(it, "yyyy-MM-dd HH:mm:ss")) }
                entry.reminderOutcome?.let { DetailLine("提醒结果", it) }
                entry.transactionType?.let { DetailLine("收支方向", if (it == TransactionType.INCOME) "收入" else "支出") }
                entry.merchant?.let { DetailLine("商户", it) }
                entry.paymentMethod?.let { DetailLine("付款方式", it) }
                DetailLine("通知标题", entry.title.ifBlank { "未提供" })
                DetailLine("通知正文", entry.text.ifBlank { "无可用正文，或非交易通知未保留正文" })
                DetailLine("来源包名", entry.packageName)
                Button(onClick = { clipboard.setText(AnnotatedString(entry.formatted)); copied = true }, modifier = Modifier.fillMaxWidth()) { Text(if (copied) "已复制详情" else "复制详情") }
                if (entry.source != "屏幕补充" && entry.status != RecognitionStatus.SERVICE && entry.text.isNotBlank()) {
                    OutlinedButton(onClick = { onTest(entry.id) }, modifier = Modifier.fillMaxWidth()) { Text("用此记录测试") }
                }
                if ((entry.needsAttention || (entry.status == RecognitionStatus.SKIPPED && entry.reason.contains("五分钟"))) && entry.text.isNotBlank()) {
                    OutlinedButton(onClick = { onManualAdd(entry.id) }, modifier = Modifier.fillMaxWidth()) { Text("带入手动记账") }
                    Text("仅填入草稿，请核对账本与账户后再保存。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}
