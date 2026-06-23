package com.financetracker.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.ui.theme.CardSmall

enum class KeypadKey {
    N0, N1, N2, N3, N4, N5, N6, N7, N8, N9,
    DOT, BACKSPACE, ADD, SUBTRACT, EQUALS, SAVE, NEXT_RECORD,
}

// 1.0.9 UI 规范: 数字键盘 - 按键 CardSmall(12dp), 主按钮(保存)CardSmall+主色填充+白字
@Composable
fun CalculatorKeypad(
    showEquals: Boolean,
    selectedAccount: PaymentAccount?,
    onAccountClick: () -> Unit,
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Account indicator
        if (selectedAccount != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    modifier = Modifier.clickable(onClick = onAccountClick),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = CardSmall,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountLogo(type = selectedAccount.type, accountName = selectedAccount.name, size = 16.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            selectedAccount.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("▾", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Row 1: 7 8 9 ⌫
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyButton("7", KeypadKey.N7, onKey, Modifier.weight(1f))
            KeyButton("8", KeypadKey.N8, onKey, Modifier.weight(1f))
            KeyButton("9", KeypadKey.N9, onKey, Modifier.weight(1f))
            KeyButton("⌫", KeypadKey.BACKSPACE, onKey, Modifier.weight(1f), accent = true)
        }

        // Row 2: 4 5 6 +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyButton("4", KeypadKey.N4, onKey, Modifier.weight(1f))
            KeyButton("5", KeypadKey.N5, onKey, Modifier.weight(1f))
            KeyButton("6", KeypadKey.N6, onKey, Modifier.weight(1f))
            KeyButton("+", KeypadKey.ADD, onKey, Modifier.weight(1f), accent = true)
        }

        // Row 3: 1 2 3 -
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyButton("1", KeypadKey.N1, onKey, Modifier.weight(1f))
            KeyButton("2", KeypadKey.N2, onKey, Modifier.weight(1f))
            KeyButton("3", KeypadKey.N3, onKey, Modifier.weight(1f))
            KeyButton("−", KeypadKey.SUBTRACT, onKey, Modifier.weight(1f), accent = true)
        }

        // Row 4: . 0 再记 保存/=
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyButton(".", KeypadKey.DOT, onKey, Modifier.weight(1f))
            KeyButton("0", KeypadKey.N0, onKey, Modifier.weight(1f))
            KeyButton("再记", KeypadKey.NEXT_RECORD, onKey, Modifier.weight(1f), accent = true)
            SaveButton(showEquals, onKey, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    key: KeypadKey,
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    OutlinedButton(
        onClick = { onKey(key) },
        modifier = modifier.height(52.dp),
        shape = CardSmall,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (accent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 1) 14.sp else 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// 1.0.9 UI 规范: 主按钮(保存)圆角 12dp + 主色填充 + 白字 + padding vertical 12 / horizontal 24
@Composable
private fun SaveButton(
    showEquals: Boolean,
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (showEquals) "=" else "保存"
    Button(
        onClick = { onKey(if (showEquals) KeypadKey.EQUALS else KeypadKey.SAVE) },
        modifier = modifier.height(52.dp),
        shape = CardSmall,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 12.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = label,
            fontSize = if (showEquals) 22.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

