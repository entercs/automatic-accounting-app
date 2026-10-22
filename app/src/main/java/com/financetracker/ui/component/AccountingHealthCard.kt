package com.financetracker.ui.component

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.financetracker.notification.*
import com.financetracker.notification.parser.PaymentParsers
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.theme.CardLarge

@Composable
fun AccountingHealthCard(onManualAdd: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by PaymentMonitor.status.collectAsState()
    val accessibilityAuthorized by AccessibilityStateHolder.isEnabled.collectAsState()
    val accessibilityConnected by AccessibilityStateHolder.isConnected.collectAsState()
    var showDiagnostics by remember { mutableStateOf(false) }
    var resultNotificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resultNotificationsEnabled = it }
    var recoveryRequested by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resultNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                PaymentMonitor.refresh(context)
                AccessibilityStateHolder.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sensors, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("自动记账", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                SuggestionChip(onClick = { showDiagnostics = true }, label = { Text("识别记录") })
            }
            Text("通知读取 · 主要通道", style = MaterialTheme.typography.bodyLarge)
            Text(when {
                state.connected -> "已连接，等待新的支付通知"
                state.authorized -> "已授权，但服务尚未连接"
                else -> "未授权，开启后可自动识别支付通知"
            }, color = if (state.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.authorized && !state.connected) {
                    Button(onClick = { PaymentMonitor.reconnect(context); recoveryRequested = true }) { Text("尝试恢复") }
                }
                if (!state.authorized) {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("开启通知读取") }
                } else {
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("管理授权") }
                }
            }
            if (recoveryRequested && !state.connected) Text("已请求系统重新连接。若仍未恢复，请在管理授权中关闭后重新开启。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("屏幕识别 · 可选补充", style = MaterialTheme.typography.bodyLarge)
                    Text(when {
                        accessibilityConnected -> "已连接，可补充识别支付成功页面"
                        accessibilityAuthorized -> "已授权，服务未连接，请重新开启"
                        else -> "未开启，不影响通知读取"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("设置") }
            }
            var showWhitelist by remember { mutableStateOf(false) }
            TextButton(onClick = { showWhitelist = !showWhitelist }, contentPadding = PaddingValues(0.dp)) { Text(if (showWhitelist) "收起屏幕识别白名单" else "查看屏幕识别白名单") }
            if (showWhitelist) {
                Text(ScreenCapturePolicy.apps.joinToString("、") { it.label }, style = MaterialTheme.typography.bodyMedium)
                Text("仅以上应用可读取支付页面。银行 App 不在屏幕白名单内，银行卡动账仍通过通知读取处理。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("部分银行会检测无障碍服务是否开启，即使不读取银行页面也可能拦截。遇到此类提示，请关闭屏幕识别；通知读取仍可使用。关闭后需到系统设置手动重新开启。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (accessibilityConnected) OutlinedButton(onClick = {
                if (!FinanceNotificationService.disableScreenRecognition()) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("关闭屏幕识别") }
            Text("没有通知、通知隐藏金额或支付页面无法读取时，仍需手动补记。银行短信暂不读取。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!resultNotificationsEnabled && android.os.Build.VERSION.SDK_INT >= 33) TextButton(onClick = { permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, contentPadding = PaddingValues(0.dp)) { Text("开启记账结果提醒") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.BatteryFull, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("后台耗电管理")
            }
        }
    }
    if (showDiagnostics) RecognitionDiagnostics(onDismiss = { showDiagnostics = false }, onManualAdd = onManualAdd)
}

@Composable
private fun RecognitionDiagnostics(onDismiss: () -> Unit, onManualAdd: () -> Unit) {
    val logs by NotificationLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var showOnlyPending by remember { mutableStateOf(true) }
    var showTester by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pkg by remember { mutableStateOf("com.tencent.mm") }
    var title by remember { mutableStateOf("微信支付") }
    var body by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val visible = logs.filter { !showOnlyPending || (it.outcome != "已记账" && it.outcome != "重复已跳过") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("识别记录", style = MaterialTheme.typography.headlineSmall)
                            Text("最近 100 条样本，仅保存在本机", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = showOnlyPending, onClick = { showOnlyPending = true }, label = { Text("待补充") })
                        FilterChip(selected = !showOnlyPending, onClick = { showOnlyPending = false }, label = { Text("全部") })
                        TextButton(onClick = { showTester = !showTester; result = null }) { Text("样本测试") }
                    }
                    Row {
                        TextButton(enabled = logs.isNotEmpty(), onClick = {
                            clipboard.setText(AnnotatedString(visible.joinToString("\n\n") { it.formatted })); copied = true
                        }) { Text(if (copied) "已复制" else "复制当前记录") }
                        TextButton(enabled = logs.isNotEmpty(), onClick = { confirmClear = true }) { Text("清空记录") }
                    }
                }
                if (showTester) item {
                    Card(shape = CardLarge) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("测试一条通知", style = MaterialTheme.typography.titleMedium)
                            Text("只预览识别结果，不写入账本。修改内容后请重新测试。", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(pkg, { pkg = it; result = null }, label = { Text("来源应用包名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(title, { title = it; result = null }, label = { Text("通知标题") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(body, { body = it; result = null }, label = { Text("通知正文") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                            Button(enabled = body.isNotBlank(), onClick = {
                                val parsed = PaymentParsers.parse(pkg.trim(), title, body)
                                result = if (!PaymentParsers.supports(pkg.trim())) "暂未支持此来源，可复制样本供后续补充。"
                                else parsed?.let { "识别为${if (it.transactionType == TransactionType.INCOME) "收入" else "支出"} ¥%.2f · ${it.merchant}".format(it.amount) }
                                    ?: "暂未识别为已完成交易，请手动补记并保留样本。"
                            }) { Text("测试识别") }
                            result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
                if (visible.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(if (showOnlyPending) "还没有待补充的样本" else "还没有识别记录", style = MaterialTheme.typography.titleMedium)
                        Text("开启监听后，支持的支付应用发来新通知时会显示在这里。", style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(visible) { entry ->
                    Card(shape = CardLarge) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.outcome, color = if (entry.outcome == "已记账") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            Text(entry.formatted, style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(entry.formatted)); copied = true }) { Text("复制样本") }
                                if (entry.source != "屏幕补充") TextButton(onClick = { pkg = entry.packageName; title = entry.title; body = entry.text; result = null; showTester = true; scope.launch { listState.animateScrollToItem(0) } }) { Text("填入测试") }
                                if (entry.outcome != "已记账" && entry.outcome != "重复已跳过") TextButton(onClick = { onDismiss(); onManualAdd() }) { Text("手动补记") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空识别记录？") },
        text = { Text("仅清空本机诊断样本，已保存的账单不受影响。") },
        confirmButton = { TextButton(onClick = { NotificationLogger.clear(); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
