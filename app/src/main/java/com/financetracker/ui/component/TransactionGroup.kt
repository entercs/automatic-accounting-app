package com.financetracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.theme.Divider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyGroup(
    val dateLabel: String,       // "5月8日 周四"
    val transactions: List<Transaction>,
    val totalExpense: Double,
    val totalIncome: Double,
)

fun groupTransactionsByDay(transactions: List<Transaction>): List<DailyGroup> {
    val dayFormat = SimpleDateFormat("M月d日", Locale.CHINA)
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val yearFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    val cal = Calendar.getInstance()

    val grouped = transactions.groupBy { tx ->
        cal.timeInMillis = tx.date
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        "$year-$month-$day"
    }

    return grouped.map { (_, txs) ->
        val date = txs.first().date
        cal.timeInMillis = date
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            1 -> "周日"; 2 -> "周一"; 3 -> "周二"; 4 -> "周三"
            5 -> "周四"; 6 -> "周五"; 7 -> "周六"; else -> ""
        }
        val formatter = if (cal.get(Calendar.YEAR) == currentYear) dayFormat else yearFormat
        val label = "${formatter.format(Date(date))} $dayOfWeek"
        DailyGroup(
            dateLabel = label,
            transactions = txs.sortedByDescending { it.date },
            totalExpense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            totalIncome = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        )
    }.sortedByDescending { it.transactions.first().date }
}

// 1.0.9 UI 规范: 日汇总头部 — 日期 bodyMedium 二级文字, 收支汇总 bodySmall 主/支出色
@Composable
fun DateHeader(label: String, totalExpense: Double, totalIncome: Double) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (totalExpense > 0) {
                Text(
                    "支出 ${String.format("%.2f", totalExpense)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (totalIncome > 0) {
                Text(
                    "收入 ${String.format("%.2f", totalIncome)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(color = Divider)
    }
}

