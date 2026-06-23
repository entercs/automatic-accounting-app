package com.financetracker.ui.screen.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.financetracker.ui.component.SegmentedTab
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.data.repository.StatisticsRepository
import com.financetracker.di.AppModule
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.component.CategoryLogo
import com.financetracker.ui.component.PieChart
import com.financetracker.ui.component.PieSlice
import com.financetracker.ui.theme.Amber500
import com.financetracker.ui.theme.CardLarge
import com.financetracker.ui.theme.CardInnerPadding
import com.financetracker.ui.theme.PieChartColors
import com.financetracker.ui.theme.softCardShadow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val factory = remember { StatisticsViewModel.Factory(AppModule.statisticsRepository, AppModule.transactionRepository) }
    val viewModel: StatisticsViewModel = viewModel(factory = factory)

    LaunchedEffect(Unit) { viewModel.setPeriod(StatPeriod.MONTH) }

    val period by viewModel.period.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val headerText by viewModel.headerText.collectAsState()
    val expenseTotal by viewModel.expenseTotal.collectAsState()
    val incomeTotal by viewModel.incomeTotal.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val expenseDailyTotals by viewModel.expenseDailyTotals.collectAsState()
    val incomeDailyTotals by viewModel.incomeDailyTotals.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val prevExpenseTotal by viewModel.prevExpenseTotal.collectAsState()
    val prevIncomeTotal by viewModel.prevIncomeTotal.collectAsState()
    val monthlyExpenses by viewModel.monthlyExpenses.collectAsState()
    val yearlyTotalExpense by viewModel.yearlyTotalExpense.collectAsState()
    val canGoNext by viewModel.canGoNext.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showPeriodPicker by remember { mutableStateOf(false) }

    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold { padding ->
        // 1.0.9 UI 规范: 把 selType/typeColor 提到 Scaffold content lambda(Composable context),
        //  否则在 LazyColumn item 外直接读 MaterialTheme.colorScheme 会编译失败
        val selType = selectedType
        val typeColor = if (selType == TransactionType.EXPENSE) MaterialTheme.colorScheme.error
            else if (selType == TransactionType.INCOME) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1.0.9 UI 规范: 周/月/年 Tab - 用 SegmentedTab 替代 FilterChip(禁止色块高亮)
            item {
                SegmentedTab(
                    options = listOf(
                        "周" to StatPeriod.WEEK,
                        "月" to StatPeriod.MONTH,
                        "年" to StatPeriod.YEAR,
                        "自定义" to StatPeriod.CUSTOM,
                    ),
                    selected = period,
                    onSelect = { viewModel.setPeriod(it) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Date navigation
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.previous() }) {
                        Text("◀", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clickable { if (period == StatPeriod.CUSTOM) showDatePicker = true },
                    )
                    IconButton(
                        onClick = { viewModel.next() },
                        enabled = canGoNext,
                    ) {
                        Text("▶", style = MaterialTheme.typography.titleMedium, color = if (canGoNext) MaterialTheme.colorScheme.onSurface else Color.LightGray)
                    }
                }
            }

            // 1.0.9 UI 规范: 收支概览卡片 - 白色背景 CardLarge,只文字用主/支出色,不用色块填充
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val expenseSelected = selectedType == TransactionType.EXPENSE
                    Card(
                        modifier = Modifier.weight(1f).clickable { viewModel.toggleType(TransactionType.EXPENSE) }.softCardShadow(),
                        shape = CardLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(CardInnerPadding)) {
                            Text(
                                "支出 ▼",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format("%.2f", expenseTotal),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = if (expenseSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                    val incomeSelected = selectedType == TransactionType.INCOME
                    Card(
                        modifier = Modifier.weight(1f).clickable { viewModel.toggleType(TransactionType.INCOME) }.softCardShadow(),
                        shape = CardLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(CardInnerPadding)) {
                            Text(
                                "收入 ▼",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format("%.2f", incomeTotal),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = if (incomeSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // When a type is selected, show pie chart + trend chart for that type
            if (selType != null) {
                val summaries = if (selType == TransactionType.EXPENSE) expenseCategories else incomeCategories
                val dailyTotals = if (selType == TransactionType.EXPENSE) expenseDailyTotals else incomeDailyTotals
                val typeLabel = if (selType == TransactionType.EXPENSE) "支出" else "收入"

                // Pie chart — 规范要求 CardLarge(16dp) + softCardShadow,取消 protrusion
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().softCardShadow(),
                        shape = CardLarge,
                    ) {
                        Column(
                            modifier = Modifier.padding(CardInnerPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${typeLabel}分类", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (summaries.isNotEmpty()) {
                                val slices = summaries.mapIndexed { i, s ->
                                    PieSlice(
                                        label = "${s.category.name}",
                                        value = s.amount.toFloat(),
                                        color = PieChartColors[i % PieChartColors.size],
                                    )
                                }
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PieChart(slices = slices)
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                    Text("暂无${typeLabel}数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Trend chart
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().softCardShadow(),
                        shape = CardLarge,
                    ) {
                        Column(modifier = Modifier.padding(CardInnerPadding)) {
                            Text("${typeLabel}趋势", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (dailyTotals.isNotEmpty()) {
                                // 1.0.9 UI 规范: 趋势线用暖橙 Amber500
                                TrendChart(
                                    dailyTotals = dailyTotals,
                                    startDate = startDate,
                                    endDate = endDate,
                                    lineColor = Amber500,
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                    Text("暂无${typeLabel}趋势数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Category list
                if (summaries.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().softCardShadow(),
                            shape = CardLarge,
                        ) {
                            Column(modifier = Modifier.padding(CardInnerPadding)) {
                                Text("分类明细", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                summaries.forEach { cs ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CategoryLogo(categoryName = cs.category.name, categoryIcon = cs.category.icon, size = 24.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cs.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(String.format("%.2f", cs.amount), style = MaterialTheme.typography.bodyLarge, color = typeColor)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(String.format("%.1f", cs.percentage * 100) + "%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Overview: comparison + yearly (when not toggled)
                if (prevExpenseTotal > 0) {
                    item {
                        val diff = expenseTotal - prevExpenseTotal
                        val pct = if (prevExpenseTotal > 0) (diff / prevExpenseTotal * 100) else 0.0
                        val isUp = diff > 0
                        val compColor = if (isUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Card(
                            modifier = Modifier.fillMaxWidth().softCardShadow(),
                            shape = CardLarge,
                        ) {
                            Column(modifier = Modifier.padding(CardInnerPadding)) {
                                Text("环比对比", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("上期支出 ${String.format("%.2f", prevExpenseTotal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("本期支出 ${String.format("%.2f", expenseTotal)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val maxVal = maxOf(expenseTotal, prevExpenseTotal).toFloat().coerceAtLeast(1f)
                                val prevW = (prevExpenseTotal / maxVal).toFloat().coerceAtLeast(0.05f)
                                val currW = (expenseTotal / maxVal).toFloat().coerceAtLeast(0.05f)
                                Row(modifier = Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(prevW).fillMaxHeight().padding(end = 2.dp)) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawRoundRect(Color.Gray.copy(alpha = 0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
                                        }
                                    }
                                    Box(modifier = Modifier.weight(currW).fillMaxHeight().padding(start = 2.dp)) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawRoundRect(compColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (isUp) "比上期多花 ${String.format("%.1f", pct)}%" else "比上期少花 ${String.format("%.1f", kotlin.math.abs(pct))}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = compColor,
                                )
                            }
                        }
                    }
                }

                // Yearly report (monthly bars)
                if (period == StatPeriod.YEAR) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().softCardShadow(),
                            shape = CardLarge,
                        ) {
                            Column(modifier = Modifier.padding(CardInnerPadding)) {
                                Text("月度趋势", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("年度总支出 ${String.format("%.2f", yearlyTotalExpense)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(12.dp))
                                if (monthlyExpenses.isNotEmpty()) {
                                    val maxM = monthlyExpenses.max().toFloat().coerceAtLeast(1f)
                                    val months = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")
                                    Row(modifier = Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        monthlyExpenses.forEachIndexed { i, v ->
                                            // 在 Composable context 里取颜色,避免 Canvas DrawScope 里调 MaterialTheme
                                            val barColor = if (v > 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.LightGray
                                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                                    val h = (v / maxM).toFloat().coerceAtLeast(0.03f)
                                                    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight(h)) {
                                                        drawRoundRect(barColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(months[i], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        }
    }

    // Custom range date picker
    if (showDatePicker && period == StatPeriod.CUSTOM) {
        val now = System.currentTimeMillis()
        var pickStart by remember { mutableStateOf(startDate) }
        var pickEnd by remember { mutableStateOf(endDate) }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("选择日期范围") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val sdfHeader = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
                    Text("开始: ${sdfHeader.format(Date(pickStart))}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YearMonthDayPicker(pickStart) { pickStart = it }
                    }
                    HorizontalDivider()
                    Text("结束: ${sdfHeader.format(Date(pickEnd))}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YearMonthDayPicker(pickEnd, maxDate = now) { pickEnd = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCustomStartEnd(pickStart, minOf(pickEnd, now))
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TrendChart(
    dailyTotals: List<StatisticsRepository.DailyTotal>,
    startDate: Long,
    endDate: Long,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (dailyTotals.isEmpty()) return

    // Build a map of dayStart -> total
    val dataMap = dailyTotals.associate { it.dayStart to it.total }

    // Fill all days in range
    val dayMs = 86400000L
    val days = mutableListOf<Pair<Long, Double>>()
    var d = startDate
    while (d <= endDate) {
        days.add(d to (dataMap[d] ?: 0.0))
        d += dayMs
    }

    if (days.size <= 1) return

    val maxVal = days.maxOf { it.second }.toFloat().coerceAtLeast(1f)
    val cal = Calendar.getInstance()
    val yLabels = listOf(0f, maxVal / 2f, maxVal).map { String.format("%.0f", it) }

    // Compute X-axis labels: pick ~6 evenly spaced day numbers
    val labelCount = minOf(days.size, 6)
    val labelStep = if (days.size > 1) (days.size - 1).toFloat() / (labelCount - 1).toFloat().coerceAtLeast(1f) else 1f
    val labelEntries = (0 until labelCount).map { i ->
        val idx = (i * labelStep).toInt().coerceAtMost(days.size - 1)
        cal.timeInMillis = days[idx].first
        cal.get(Calendar.DAY_OF_MONTH).toString()
    }

    val textPaint = remember {
        android.graphics.Paint().apply {
            color = 0xFF666666.toInt()
            textSize = 34f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }
    val textHeight = remember { textPaint.fontMetrics.let { it.descent - it.ascent } }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padLeft = 48f
        val padBottom = 24f
        val padTop = 8f
        val padRight = 8f
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        // Draw Y-axis labels and grid lines
        val gridLines = 2 // 3 labels: max, half, 0
        for (i in 0..gridLines) {
            val y = padTop + chartH * i / gridLines
            drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(padLeft, y), Offset(w - padRight, y), strokeWidth = 1f)
            // Y label
            val yi = i
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    yLabels[gridLines - yi],
                    padLeft - 8f,
                    y + textHeight / 3f,
                    textPaint,
                )
            }
        }

        // Draw axes
        val axisColor = Color(0xFFAAAAAA)
        val bottomY = padTop + chartH
        drawLine(axisColor, Offset(padLeft, padTop), Offset(padLeft, bottomY), strokeWidth = 2f)
        drawLine(axisColor, Offset(padLeft, bottomY), Offset(w - padRight, bottomY), strokeWidth = 2f)

        // Draw line chart
        val path = Path()
        val stepX = chartW / (days.size - 1).toFloat()

        days.forEachIndexed { i, (_, amount) ->
            val x = padLeft + stepX * i
            val y = padTop + chartH * (1f - amount.toFloat() / maxVal)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, lineColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

        // Draw hollow dots
        days.forEachIndexed { i, (_, amount) ->
            val x = padLeft + stepX * i
            val y = padTop + chartH * (1f - amount.toFloat() / maxVal)
            drawCircle(lineColor, radius = 5f, center = Offset(x, y), style = Stroke(width = 2.5f))
        }

        // X-axis labels
        val xTextPaint = android.graphics.Paint().apply {
            color = 0xFF666666.toInt()
            textSize = 34f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        labelEntries.forEachIndexed { i, label ->
            val idx = (i * labelStep).toInt().coerceAtMost(days.size - 1)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    label,
                    padLeft + stepX * idx,
                    h,
                    xTextPaint,
                )
            }
        }
    }
}

@Composable
private fun YearMonthDayPicker(currentMillis: Long, maxDate: Long = System.currentTimeMillis(), onDate: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
    val nowCal = Calendar.getInstance().apply { timeInMillis = maxDate }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val maxYear = nowCal.get(Calendar.YEAR)

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        var showYear by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { showYear = true }) { Text("${year}年") }
            DropdownMenu(expanded = showYear, onDismissRequest = { showYear = false }) {
                for (y in 2020..maxYear) {
                    DropdownMenuItem(text = { Text("$y") }, onClick = { cal.set(Calendar.YEAR, y); onDate(cal.timeInMillis); showYear = false })
                }
            }
        }
        var showMonth by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { showMonth = true }) { Text("${month + 1}月") }
            DropdownMenu(expanded = showMonth, onDismissRequest = { showMonth = false }) {
                for (m in 1..12) {
                    DropdownMenuItem(text = { Text("$m") }, onClick = { cal.set(Calendar.MONTH, m - 1); cal.set(Calendar.DAY_OF_MONTH, minOf(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH))); onDate(cal.timeInMillis); showMonth = false })
                }
            }
        }
        var showDay by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { showDay = true }) { Text("${day}日") }
            DropdownMenu(expanded = showDay, onDismissRequest = { showDay = false }) {
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (d in 1..maxDay) {
                    DropdownMenuItem(text = { Text("$d") }, onClick = { cal.set(Calendar.DAY_OF_MONTH, d); onDate(cal.timeInMillis); showDay = false })
                }
            }
        }
    }
}
