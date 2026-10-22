package com.financetracker.ui.screen.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financetracker.data.repository.StatisticsRepository
import com.financetracker.di.AppModule
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.component.TrendChartScale
import com.financetracker.ui.component.trendDayIndex
import com.financetracker.ui.component.CategoryLogo
import com.financetracker.ui.component.SegmentedTab
import com.financetracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun money(value: Double) = String.format(Locale.CHINA, "%,.2f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onTransactionClick: (Long) -> Unit = {}) {
    val factory = remember { StatisticsViewModel.Factory(AppModule.statisticsRepository, AppModule.transactionRepository) }
    val vm: StatisticsViewModel = viewModel(factory = factory)
    val period by vm.period.collectAsState()
    val header by vm.headerText.collectAsState()
    val expense by vm.expenseTotal.collectAsState()
    val income by vm.incomeTotal.collectAsState()
    val expenseCategories by vm.expenseCategories.collectAsState()
    val incomeCategories by vm.incomeCategories.collectAsState()
    val expenseDaily by vm.expenseDailyTotals.collectAsState()
    val incomeDaily by vm.incomeDailyTotals.collectAsState()
    val type by vm.selectedType.collectAsState()
    val prevExpense by vm.prevExpenseTotal.collectAsState()
    val prevIncome by vm.prevIncomeTotal.collectAsState()
    val months by vm.monthlyExpenses.collectAsState()
    val canGoNext by vm.canGoNext.collectAsState()
    val start by vm.startDate.collectAsState()
    val end by vm.endDate.collectAsState()
    val refreshing by vm.isRefreshing.collectAsState()
    val error by vm.error.collectAsState()
    val transactions by vm.transactions.collectAsState()
    var showRange by remember { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val summaries = if (type == TransactionType.INCOME) incomeCategories else expenseCategories
    val daily = if (type == TransactionType.INCOME) incomeDaily else expenseDaily
    val label = if (type == TransactionType.INCOME) "收入" else "支出"
    val total = if (type == TransactionType.INCOME) income else expense
    val previous = if (type == TransactionType.INCOME) prevIncome else prevExpense
    val accent = if (type == TransactionType.INCOME) Mint500 else Brick500

    Scaffold { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        Text("收支小记", style = MaterialTheme.typography.headlineMedium)
                        Text("看见钱的去向，也看见生活的模样", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    SegmentedTab(listOf("周" to StatPeriod.WEEK, "月" to StatPeriod.MONTH, "年" to StatPeriod.YEAR, "自定义" to StatPeriod.CUSTOM), period, {
                        if (it == StatPeriod.CUSTOM) showRange = true else vm.setPeriod(it)
                    })
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::previous, enabled = period != StatPeriod.CUSTOM) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一期") }
                        TextButton(onClick = { showRange = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text(header, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = vm::next, enabled = canGoNext && period != StatPeriod.CUSTOM) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一期") }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(CardLarge).background(HeaderGradient).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("这段时间的结余", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Text("¥ ${money(income - expense)}", style = MaterialTheme.typography.displayLarge)
                        Text(if (income == 0.0 && expense == 0.0) "还没有收支，记下第一笔就会有变化" else "共 ${transactions.size} 笔账单 · 转账不计入收支", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AmountTile("支出", expense, Brick500, Modifier.weight(1f)) { vm.selectType(TransactionType.EXPENSE) }
                            AmountTile("收入", income, Mint500, Modifier.weight(1f)) { vm.selectType(TransactionType.INCOME) }
                        }
                    }
                }
                item {
                    SegmentedTab<TransactionType?>(listOf("总览" to null, "支出" to TransactionType.EXPENSE, "收入" to TransactionType.INCOME), type, vm::selectType)
                }
                if (error != null) item {
                    ReportCard {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = vm::refresh) { Text("重新加载") }
                    }
                }
                if (type == null) {
                    item { ComparisonCard("支出", expense, prevExpense, Brick500) }
                    item { ComparisonCard("收入", income, prevIncome, Mint500) }
                } else {
                    item { ComparisonCard(label, total, previous, accent) }
                    item {
                        ReportCard {
                            SectionHeading("${label}趋势", "金额（元） · 点击或横向滑动查看当天金额")
                            TrendChart(daily, start, minOf(end, System.currentTimeMillis()), accent)
                        }
                    }
                    item {
                        ReportCard {
                            SectionHeading("钱${if (type == TransactionType.INCOME) "从哪里来" else "花在哪里"}", "${summaries.size} 个分类 · 点击下方分类查看账单")
                            if (summaries.isEmpty()) EmptyReport("暂无${label}记录", "添加账单后，这里会呈现分类占比")
                            else {
                                CategoryDonut(summaries, total, label)
                                summaries.forEachIndexed { index, summary ->
                                    Column(Modifier.fillMaxWidth().clip(CardSmall).clickable { categoryId = summary.category.id }.padding(vertical = 12.dp, horizontal = 4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CategoryLogo(summary.category.name, summary.category.icon, size = 32.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(summary.category.name, style = MaterialTheme.typography.bodyLarge)
                                                Text("占比 %.1f%%".format(summary.percentage * 100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("¥ ${money(summary.amount)}", style = MaterialTheme.typography.bodyLarge)
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        LinearProgressIndicator(progress = { summary.percentage.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CardSmall),
                                            color = PieChartColors[index % PieChartColors.size], trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
                                    }
                                }
                            }
                        }
                    }
                }
                if (period == StatPeriod.YEAR) item {
                    ReportCard {
                        SectionHeading("月度支出", "全年 ¥ ${money(months.sum())}")
                        if (months.none { it > 0 }) EmptyReport("还没有年度支出", "每个月的支出会汇总在这里")
                        else MonthlyBars(months)
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
    if (showRange) {
        val dateState = rememberDateRangePickerState(initialSelectedStartDateMillis = dateForPicker(start), initialSelectedEndDateMillis = dateForPicker(minOf(end, System.currentTimeMillis())), selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= dateForPicker(System.currentTimeMillis())
        })
        DatePickerDialog(onDismissRequest = { showRange = false }, confirmButton = {
            TextButton(enabled = dateState.selectedStartDateMillis != null && dateState.selectedEndDateMillis != null, onClick = {
                vm.setCustomStartEnd(pickerToLocal(dateState.selectedStartDateMillis!!), pickerToLocal(dateState.selectedEndDateMillis!!)); showRange = false
            }) { Text("应用日期") }
        }, dismissButton = { TextButton(onClick = { showRange = false }) { Text("取消") } }) {
            DateRangePicker(state = dateState, modifier = Modifier.heightIn(max = 500.dp), title = { Text("选择统计日期", Modifier.padding(24.dp)) }, headline = null)
        }
    }
    categoryId?.let { id ->
        val name = summaries.firstOrNull { it.category.id == id }?.category?.name.orEmpty()
        val selected = transactions.filter { it.categoryId == id && it.type == type }
        AlertDialog(onDismissRequest = { categoryId = null }, title = { Text("$name · ${selected.size} 笔") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selected, key = { it.id }) { tx ->
                    Row(Modifier.fillMaxWidth().clickable { categoryId = null; onTransactionClick(tx.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tx.merchant.ifBlank { name }, style = MaterialTheme.typography.bodyLarge)
                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(tx.date)), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("¥ ${money(tx.amount)}", color = accent)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { categoryId = null }) { Text("关闭") } })
    }
}

@Composable
private fun AmountTile(label: String, value: Double, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(CardSmall).clickable(onClick = onClick).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text("¥ ${money(value)}", color = color, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = CardLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyReport(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Insights, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.tertiary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComparisonCard(label: String, value: Double, previous: Double, color: Color) {
    ReportCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${label}对比", style = MaterialTheme.typography.titleMedium)
            Text(if (previous > 0) "${if (value > previous) "增加" else "减少"} %.1f%%".format(kotlin.math.abs(value - previous) / previous * 100) else "上期无记录", color = color, style = MaterialTheme.typography.bodySmall)
        }
        val max = maxOf(value, previous, 1.0)
        listOf("本期" to value, "上期" to previous).forEachIndexed { i, (title, amount) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { (amount / max).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(7.dp).clip(CardSmall),
                    color = if (i == 0) color else MaterialTheme.colorScheme.outline, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
                Text(money(amount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CategoryDonut(summaries: List<StatisticsRepository.CategorySummary>, total: Double, label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp).semantics { contentDescription = summaries.joinToString { "${it.category.name}，${money(it.amount)}元" } }) {
            val width = 22.dp.toPx()
            val arcSize = Size(size.width - width, size.height - width)
            var angle = -90f
            summaries.forEachIndexed { i, item ->
                val sweep = item.percentage * 360
                drawArc(PieChartColors[i % PieChartColors.size], angle + 1, (sweep - 2).coerceAtLeast(0.2f), false, Offset(width / 2, width / 2), arcSize, style = Stroke(width, cap = StrokeCap.Round))
                angle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("总${label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(total), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TrendChart(data: List<StatisticsRepository.DailyTotal>, start: Long, end: Long, color: Color) {
    if (data.isEmpty()) { EmptyReport("还没有趋势", "添加账单后，点击或横向滑动曲线查看每日金额"); return }
    val days = remember(data, start, end) {
        val map = data.associate { it.dayStart to it.total }
        buildList {
            val cal = Calendar.getInstance().apply { timeInMillis = start }
            while (cal.timeInMillis <= end) { add(cal.timeInMillis to (map[cal.timeInMillis] ?: 0.0)); cal.add(Calendar.DAY_OF_MONTH, 1) }
        }
    }
    if (days.isEmpty()) return
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val peak = days.maxOf { it.second }
    val scale = remember(peak) { TrendChartScale(peak) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tickLabels = scale.ticks.map { textMeasurer.measure(scale.label(it), labelStyle) }
    val gutter = tickLabels.maxOf { it.size.width }.toFloat() + with(LocalDensity.current) { 10.dp.toPx() }
    val point = selected?.let { days.getOrNull(it) }
    val selectedText = point?.let { "${SimpleDateFormat("M月d日", Locale.CHINA).format(Date(it.first))} · ¥ ${money(it.second)}" }
        ?: "单日最高 ¥ ${money(peak)}"
    Text(selectedText, color = color, style = MaterialTheme.typography.bodyMedium)
    Canvas(Modifier.fillMaxWidth().height(164.dp)
        .semantics {
            contentDescription = "每日金额趋势，点击或横向滑动查看"
            stateDescription = selectedText
        }
        .pointerInput(days, gutter) {
            detectTapGestures { selected = trendDayIndex(it.x - gutter, size.width - gutter, 8.dp.toPx(), days.size) }
        }
        .pointerInput(days, gutter) {
            detectHorizontalDragGestures(
                onDragStart = { selected = trendDayIndex(it.x - gutter, size.width - gutter, 8.dp.toPx(), days.size) },
                onHorizontalDrag = { change, _ ->
                    selected = trendDayIndex(change.position.x - gutter, size.width - gutter, 8.dp.toPx(), days.size)
                    change.consume()
                },
            )
        }) {
        val padding = 8.dp.toPx()
        val top = maxOf(padding, tickLabels.maxOf { it.size.height } / 2f)
        val w = (size.width - gutter - padding * 2).coerceAtLeast(0f)
        val h = (size.height - top * 2).coerceAtLeast(0f)
        scale.ticks.forEachIndexed { index, _ ->
            val y = top + h * index / 4
            drawLine(grid, Offset(gutter + padding, y), Offset(size.width - padding, y), 1.dp.toPx())
            val label = tickLabels[index]
            drawText(label, topLeft = Offset(gutter - 10.dp.toPx() - label.size.width, y - label.size.height / 2f))
        }
        val points = days.mapIndexed { index, (_, amount) -> Offset(gutter + padding + w * index / (days.size - 1).coerceAtLeast(1), top + h * (1 - (amount / scale.maximum).toFloat().coerceIn(0f, 1f))) }
        val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val area = Path().apply { addPath(path); lineTo(points.last().x, size.height - top); lineTo(points.first().x, size.height - top); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .18f), color.copy(alpha = .01f))))
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        if (points.size == 1) drawCircle(color, 4.dp.toPx(), points.first())
        selected?.let { points.getOrNull(it)?.let { p -> drawLine(color.copy(alpha = .3f), Offset(p.x, top), Offset(p.x, size.height - top), 1.dp.toPx()); drawCircle(color, 5.dp.toPx(), p) } }
    }
    Row(Modifier.fillMaxWidth().padding(start = with(LocalDensity.current) { gutter.toDp() } + 8.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(SimpleDateFormat("M/d", Locale.CHINA).format(Date(start)), style = MaterialTheme.typography.bodySmall)
        Text(SimpleDateFormat("M/d", Locale.CHINA).format(Date(end)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MonthlyBars(values: List<Double>) {
    val max = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    selected?.let { Text("${it + 1}月 · ¥ ${money(values[it])}", style = MaterialTheme.typography.bodyMedium, color = Brick500) }
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        values.forEachIndexed { i, value ->
            Column(Modifier.weight(1f).fillMaxHeight().clickable { selected = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight((value / max).toFloat().coerceAtLeast(.02f)).clip(RoundedCornerShape(5.dp))
                        .background(if (selected == i) Brick500 else Brick500.copy(alpha = .45f)))
                }
                Spacer(Modifier.height(8.dp)); Text("${i + 1}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Material date pickers use UTC dates, while repository ranges use the device's local midnight.
private fun dateForPicker(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
}
private fun pickerToLocal(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply { clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
}
