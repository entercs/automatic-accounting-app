package com.financetracker.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.financetracker.launcher.LauncherIcon
import com.financetracker.launcher.LauncherIconManager
import com.financetracker.ui.theme.CardLarge

@Composable
fun LauncherIconCard() {
    val context = LocalContext.current
    val manager = remember { LauncherIconManager(context) }
    var selected by remember { mutableStateOf(manager.selected()) }
    var message by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth(), shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("桌面图标", style = MaterialTheme.typography.titleLarge)
            Text("选择喜欢的样式。切换时可能返回桌面，图标更新后可重新打开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                LauncherIcon.entries.forEach { icon ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .selectable(selected == icon, role = Role.RadioButton, onClick = {
                            runCatching { manager.select(icon) }.onSuccess { selected = manager.selected(); message = "已切换为${icon.label}" }
                                .onFailure { selected = manager.selected(); message = "切换未完成，请稍后重试" }
                        }).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(icon.preview), contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp))
                                .background(if (icon == LauncherIcon.DEFAULT) Color(0xFF4CAF50) else Color(0xFFFAF6EE)).padding(if (icon == LauncherIcon.DEFAULT) 8.dp else 2.dp))
                        Spacer(Modifier.width(14.dp)); Text(icon.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected == icon, onClick = null)
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
