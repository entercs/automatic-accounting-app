package com.financetracker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financetracker.domain.calculator.MoneyExpression
import com.financetracker.ui.theme.CardSmall
import java.math.BigDecimal

@Composable
fun BalanceCalculator(initialBalance: Double, onDone: (Double) -> Unit, modifier: Modifier = Modifier, doneLabel: String = "保存余额") {
    var expression by rememberSaveable(initialBalance) { mutableStateOf(BigDecimal.valueOf(initialBalance).stripTrailingZeros().toPlainString()) }
    var replaceOnDigit by rememberSaveable(initialBalance) { mutableStateOf(true) }
    val result = remember(expression) { MoneyExpression.evaluate(expression) }
    val hasOperation = expression.drop(1).any { it == '+' || it == '-' }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CardSmall, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.End) {
                Text("账户余额 · 支持加减", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(expression.ifBlank { "0" }, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                Text(if (hasOperation && result != null) "= ${result.toPlainString()}" else "可输入负数，表示负债", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        listOf(listOf("7", "8", "9", "⌫"), listOf("4", "5", "6", "+"), listOf("1", "2", "3", "−"), listOf("C", "0", ".", "=")).forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.forEach { label ->
                    OutlinedButton(onClick = {
                        when (label) {
                            "C" -> { expression = ""; replaceOnDigit = false }
                            "⌫" -> { expression = expression.dropLast(1); replaceOnDigit = false }
                            "=" -> result?.let { expression = it.stripTrailingZeros().toPlainString(); replaceOnDigit = true }
                            "+", "−" -> { expression = MoneyExpression.append(expression, if (label == "−") "-" else "+"); replaceOnDigit = false }
                            else -> {
                                expression = MoneyExpression.append(if (replaceOnDigit) "" else expression, label)
                                replaceOnDigit = false
                            }
                        }
                    }, modifier = Modifier.weight(1f).height(46.dp), shape = CardSmall,
                        contentPadding = PaddingValues(0.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (label in listOf("+", "−", "=")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Button(onClick = { result?.let { onDone(it.toDouble()) } }, enabled = result != null,
            modifier = Modifier.fillMaxWidth(), shape = CardSmall) { Text(doneLabel) }
    }
}
