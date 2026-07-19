package com.financetracker.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.theme.AccountIconDisplay
import com.financetracker.ui.theme.CardSmall
import com.financetracker.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1.0.9 UI 规范:
//  - 列表项圆角 12dp(CardSmall)
//  - 行高 64dp(通过 vertical 16dp padding + 32dp 图标达到)
//  - 金额颜色: 收入 colorScheme.primary, 支出 colorScheme.error, 转账 onSurfaceVariant
//  - 金额字号: bodyLarge(16sp/600)
//  - 标题字号: titleLarge(18sp/600)? 改 bodyLarge(16sp/600) 适配 64dp 行高
//  - 商户/时间: bodySmall(12sp/400) onSurfaceVariant
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    category: Category?,
    account: PaymentAccount?,
    toAccount: PaymentAccount? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(account?.color ?: "#757575"))
    } catch (_: Exception) {
        TextTertiary
    }

    Card(
        modifier = modifier.fillMaxWidth().then(
            if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            else Modifier.clickable(onClick = onClick)
        ),
        shape = CardSmall,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        val isTransfer = transaction.type == TransactionType.TRANSFER

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category icon
            // 1.1.1: 转账走专门的 TransferLogo(ic_transfer drawable),其他走 CategoryLogo
            if (isTransfer) {
                TransferLogo(size = 32.dp)
            } else {
                CategoryLogo(
                    categoryName = category?.name ?: "",
                    categoryIcon = category?.icon ?: "📂",
                    size = 32.dp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                // Category name as primary
                val titleText = if (isTransfer) "转账" else (category?.name ?: "未知分类")
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(2.dp))

                // Merchant (if exists)
                if (transaction.merchant.isNotBlank()) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Account badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountIconDisplay(
                        type = account?.type ?: "",
                        accountName = account?.name ?: "",
                        size = 16.dp,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = account?.name ?: "未知账户",
                        style = MaterialTheme.typography.bodySmall,
                        color = bgColor,
                    )
                    if (isTransfer && toAccount != null) {
                        Text(" → ", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        Text(toAccount.name, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(transaction.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Amount
            val prefix = when {
                isTransfer -> "↔"
                transaction.type == TransactionType.INCOME -> "+"
                else -> "-"
            }
            val color = when {
                isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
                transaction.type == TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            Text(
                text = "$prefix${String.format("%.2f", transaction.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
private fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))
