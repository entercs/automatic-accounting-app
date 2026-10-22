package com.financetracker.ui.component

import android.os.Build
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

@Composable
fun DateTimeWheelDialog(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var draftMillis by rememberSaveable(initialMillis) { mutableLongStateOf(initialMillis) }
    val draft = DateTimeSelection.fromEpochMillis(draftMillis)
    fun update(value: DateTimeSelection) { draftMillis = value.toEpochMillis() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("交易时间") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DateTimeWheel("年", draft.year, minOf(1900, draft.year)..maxOf(2100, draft.year), false, Modifier.weight(1.4f)) { update(draft.withYear(it)) }
                DateTimeWheel("月", draft.month, 1..12, true, Modifier.weight(1f)) { update(draft.withMonth(it)) }
                DateTimeWheel("日", draft.day, 1..draft.daysInMonth, true, Modifier.weight(1f)) { update(draft.copy(day = it)) }
                DateTimeWheel("时", draft.hour, 0..23, true, Modifier.weight(1f)) { update(draft.copy(hour = it)) }
                DateTimeWheel("分", draft.minute, 0..59, true, Modifier.weight(1f)) { update(draft.copy(minute = it)) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft.toEpochMillis()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DateTimeWheel(label: String, value: Int, range: IntRange, wrap: Boolean, modifier: Modifier, onChange: (Int) -> Unit) {
    val currentOnChange by rememberUpdatedState(onChange)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(164.dp),
            factory = { context ->
                NumberPicker(context).apply {
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setFormatter { number -> if (label == "年") number.toString() else String.format(Locale.CHINA, "%02d", number) }
                    setOnValueChangedListener { _, _, newValue -> currentOnChange(newValue) }
                }
            },
            update = { picker ->
                picker.minValue = range.first
                picker.maxValue = range.last
                picker.wrapSelectorWheel = wrap
                picker.value = value
                picker.contentDescription = "$label，当前$value"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) picker.textColor = textColor
            },
        )
    }
}
