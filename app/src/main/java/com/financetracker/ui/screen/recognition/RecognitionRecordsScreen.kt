package com.financetracker.ui.screen.recognition

import com.financetracker.domain.calculator.Money
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.notification.*
import com.financetracker.ui.theme.CardLarge
import java.text.SimpleDateFormat
import java.time.*
import java.util.Date
import java.util.Locale

internal fun recordTime(value: Long, pattern: String = "MM-dd HH:mm:ss") = SimpleDateFormat(pattern, Locale.CHINA).format(Date(value))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecognitionRecordsScreen(onBack: () -> Unit, onDetail: (String) -> Unit, onTest: () -> Unit) {
    val logs by NotificationLogger.logs.collectAsState()
    val connection by PaymentMonitor.status.collectAsState()
    var filter by rememberSaveable { mutableStateOf(RecognitionFilter()) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val visible = remember(logs, filter) { logs.filter(filter::matches) }
    LaunchedEffect(Unit) {
        while (true) { NotificationLogger.refresh(); kotlinx.coroutines.delay(60_000) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("识别记录") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                TextButton(onClick = onTest) { Text("测试通知") }
                Box {
                    IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, "更多操作") }
                    DropdownMenu(showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(text = { Text("复制筛选结果（${visible.size}条）") }, enabled = visible.isNotEmpty(), onClick = {
                            clipboard.setText(AnnotatedString(visible.joinToString("\n\n") { it.formatted })); copied = true; showMore = false
                        })
                        DropdownMenuItem(text = { Text("清空全部识别记录") }, onClick = { confirmClear = true; showMore = false })
                    }
                }
            })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (connection.connected) "通知读取已连接" else if (connection.authorized) "通知已授权，当前未连接" else "通知读取未授权",
                        style = MaterialTheme.typography.titleMedium, color = if (connection.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("保留近7天，最多1000条 · 仅保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (logs.isNotEmpty()) Text("已保留记录：${recordTime(logs.last().timestamp, "MM-dd HH:mm")} 至 ${recordTime(logs.first().timestamp, "MM-dd HH:mm")}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(filter.attentionOnly, onClick = { filter = filter.copy(attentionOnly = !filter.attentionOnly, statuses = emptySet()) }, label = { Text("需关注") })
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { showFilter = true }) { Text("筛选") }
                        Spacer(Modifier.weight(1f))
                        Text("${visible.size}条", style = MaterialTheme.typography.labelLarge)
                    }
                    val labels = filterLabels(filter)
                    if (labels.isNotEmpty()) Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    if (copied) Text("已复制当前筛选结果", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (visible.isEmpty()) item {
                Card(Modifier.fillMaxWidth(), shape = CardLarge) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (logs.isEmpty()) "还没有识别记录" else "没有符合筛选条件的记录", style = MaterialTheme.typography.titleMedium)
                        Text(if (logs.isEmpty()) "支持的应用发来新通知、或通知服务连接变化时，会在这里留下记录。测试通知只预览结果。" else "可以调整应用、时间、处理结果或采集方式。更早或超出条数上限的记录可能已清理。", style = MaterialTheme.typography.bodyMedium)
                        if (filter != RecognitionFilter()) TextButton(onClick = { showFilter = true }) { Text("调整筛选") }
                    }
                }
            }
            val groups = visible.groupBy { recordTime(it.timestamp, "yyyy年MM月dd日") }
            groups.forEach { (day, records) ->
                item(key = "day-$day") { Text(day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                items(records, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth().clickable { onDetail(entry.id) }, shape = CardLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.appLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text(entry.status.label, style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.needsAttention) MaterialTheme.colorScheme.error else if (entry.status == RecognitionStatus.RECORDED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(entry.title.ifBlank { "无标题通知" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            if (entry.parsedAmount != null) Text("${if (entry.transactionType == com.financetracker.domain.model.TransactionType.INCOME) "+" else "−"} ¥${Money.format(entry.parsedAmount)}", style = MaterialTheme.typography.titleLarge)
                            Text("${recordTime(entry.timestamp, "HH:mm:ss")} · ${entry.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.reason.isNotBlank()) Text(entry.reason, maxLines = if (entry.status == RecognitionStatus.SERVICE) 1 else 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (showFilter) RecognitionFilterDialog(filter, onDismiss = { showFilter = false }, onApply = { filter = it; showFilter = false })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空全部识别记录？") },
        text = { Text("清空所有应用的本机识别与服务记录，包含当前筛选之外的记录。账单和账户余额不受影响。") },
        confirmButton = { TextButton(onClick = { NotificationLogger.clear(); confirmClear = false }) { Text("清空全部") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}

private fun filterLabels(filter: RecognitionFilter): List<String> = buildList {
    addAll(filter.applications.map { id -> RecognitionApps.apps.find { it.id == id }?.label ?: if (id == NotificationLogEntry.SERVICE_PACKAGE) "通知服务" else id })
    addAll(filter.statuses.map { it.label }); addAll(filter.channels)
    if (filter.start != null) add("${recordTime(filter.start, "MM-dd")}—${filter.endExclusive?.let { recordTime(it - 1, "MM-dd") } ?: "至今"}")
    if (filter.attentionOnly) add("需关注")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecognitionFilterDialog(initial: RecognitionFilter, onDismiss: () -> Unit, onApply: (RecognitionFilter) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var confirmReset by remember { mutableStateOf(false) }
    var showDates by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    fun dayStart(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("筛选识别记录") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("应用来源", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (RecognitionApps.apps.map { it.id to it.label } + (NotificationLogEntry.SERVICE_PACKAGE to "通知服务")).forEach { (id, label) ->
                    FilterChip(id in draft.applications, { draft = draft.copy(applications = draft.applications.toggle(id)) }, label = { Text(label) })
                }
            }
            Text("时间", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(draft.start == null, { draft = draft.copy(start = null, endExclusive = null) }, label = { Text("全部日期") })
                FilterChip(draft.start == dayStart(LocalDate.now()), { draft = draft.copy(start = dayStart(LocalDate.now()), endExclusive = dayStart(LocalDate.now().plusDays(1))) }, label = { Text("今天") })
                FilterChip(draft.start == dayStart(LocalDate.now().minusDays(6)), { draft = draft.copy(start = dayStart(LocalDate.now().minusDays(6)), endExclusive = dayStart(LocalDate.now().plusDays(1))) }, label = { Text("近7天") })
                OutlinedButton(onClick = { showDates = true }) { Text("自定义日期") }
            }
            if (draft.start != null) Text("${recordTime(draft.start!!, "yyyy-MM-dd")} 至 ${recordTime((draft.endExclusive ?: System.currentTimeMillis()) - 1, "yyyy-MM-dd")}", style = MaterialTheme.typography.bodySmall)
            Text("处理结果", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RecognitionStatus.entries.forEach { status -> FilterChip(status in draft.statuses, { draft = draft.copy(statuses = draft.statuses.toggle(status), attentionOnly = false) }, label = { Text(status.label) }) }
            }
            Text("采集方式", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("通知读取", "无障碍通知", "屏幕补充", "服务状态").forEach { channel -> FilterChip(channel in draft.channels, { draft = draft.copy(channels = draft.channels.toggle(channel)) }, label = { Text(channel) }) }
            }
            Text("同一项可多选，各项之间组合匹配。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onApply(draft) }) { Text("应用筛选") } }, dismissButton = {
        Row { TextButton(onClick = { confirmReset = true }) { Text("重置") }; TextButton(onClick = onDismiss) { Text("取消") } }
    })
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("重置筛选？") }, text = { Text("清除所有筛选条件并立即显示全部保留记录。") },
        confirmButton = { TextButton(onClick = { confirmReset = false; onApply(RecognitionFilter()) }) { Text("确认重置") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } })
    if (showDates) {
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = draft.start?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            initialSelectedEndDateMillis = draft.endExclusive?.let { Instant.ofEpochMilli(it - 1).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() })
        DatePickerDialog(onDismissRequest = { showDates = false }, confirmButton = {
            TextButton(enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null, onClick = {
                draft = draft.copy(start = dayStart(Instant.ofEpochMilli(picker.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()),
                    endExclusive = dayStart(Instant.ofEpochMilli(picker.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)))
                showDates = false
            }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { showDates = false }) { Text("取消") } }) {
            DateRangePicker(picker, Modifier.height(440.dp), title = { Text("选择记录日期", Modifier.padding(16.dp)) })
        }
    }
}

private fun <T> Set<T>.toggle(value: T) = if (value in this) this - value else this + value
