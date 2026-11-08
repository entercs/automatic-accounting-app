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
fun AccountingHealthCard(onOpenRecords: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by PaymentMonitor.status.collectAsState()
    val accessibilityAuthorized by AccessibilityStateHolder.isEnabled.collectAsState()
    val accessibilityConnected by AccessibilityStateHolder.isConnected.collectAsState()
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
                SuggestionChip(onClick = onOpenRecords, label = { Text("识别记录") })
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
                    Text("后台保护", style = MaterialTheme.typography.bodyLarge)
                    Text(when {
                        !state.protectionEnabled -> "已关闭，仍可接收通知，但后台恢复保护停止"
                        !state.authorized -> "开启通知读取后运行"
                        state.protectionRunning -> "运行中，通知断开后会自动重试"
                        else -> "尚未运行，请允许后台运行后重试"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.protectionEnabled, onCheckedChange = { PaymentMonitor.setProtectionEnabled(context, it) })
            }
            Text("启用后会保留一条后台运行通知。小米/Redmi建议允许自启动、将省电策略设为无限制，并锁定最近任务中的记账助手；系统强行停止应用后仍需手动打开恢复。", style = MaterialTheme.typography.bodySmall)
            state.protectionIssue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state.authorized && state.protectionEnabled && !state.protectionRunning) {
                OutlinedButton(onClick = { AccountingProtectionService.ensureRunning(context); PaymentMonitor.reconnect(context) }) { Text("恢复后台保护") }
            }
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
}
