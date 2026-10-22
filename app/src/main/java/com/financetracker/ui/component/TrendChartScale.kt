package com.financetracker.ui.component

import java.math.BigDecimal
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Four equal intervals with readable monetary tick values, including an all-zero series. */
class TrendChartScale(peak: Double) {
    private val step: Double
    val maximum: Double
    val ticks: List<Double>

    init {
        val rawStep = (if (peak > 0) peak else 1.0) / 4
        val magnitude = 10.0.pow(floor(log10(rawStep)))
        val fraction = rawStep / magnitude
        val niceFraction = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= fraction - 1e-9 }
        step = (niceFraction * magnitude).coerceAtLeast(0.01)
        maximum = step * 4
        ticks = (4 downTo 0).map { it * step }
    }

    fun label(value: Double): String {
        val unit = if (maximum >= 100_000_000) 100_000_000.0 else if (maximum >= 10_000) 10_000.0 else 1.0
        val suffix = if (unit == 100_000_000.0) "亿" else if (unit == 10_000.0) "万" else ""
        val decimals = ceil(-log10(step / unit)).toInt().coerceIn(0, 8) + 1
        return BigDecimal.valueOf(value / unit).setScale(decimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix
    }
}

/** Match the nearest drawn day, accounting for the same inset as the chart path. */
fun trendDayIndex(x: Float, width: Float, inset: Float, count: Int): Int {
    if (count <= 1 || width <= inset * 2) return 0
    return (((x - inset) / (width - inset * 2)).coerceIn(0f, 1f) * (count - 1)).roundToInt()
}
