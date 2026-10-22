package com.financetracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartScaleTest {
    @Test fun monetaryTicksCoverPeakInEqualIntervals() {
        val scale = TrendChartScale(98.0)
        assertEquals(listOf(100.0, 75.0, 50.0, 25.0, 0.0), scale.ticks)
        assertEquals(100.0, scale.maximum, 0.0001)
    }

    @Test fun zeroSeriesHasUsableNonzeroAxis() {
        val scale = TrendChartScale(0.0)
        assertEquals(1.0, scale.maximum, 0.0001)
        assertEquals("0", scale.label(scale.ticks.last()))
    }

    @Test fun smallCurrencyAmountsHaveDistinctLabels() {
        val scale = TrendChartScale(0.03)
        assertTrue(scale.maximum >= 0.03)
        assertEquals(listOf("0.04", "0.03", "0.02", "0.01", "0"), scale.ticks.map(scale::label))
    }

    @Test fun largeCurrencyLabelsPreserveFractionalUnits() {
        val scale = TrendChartScale(9000.0)
        assertEquals(listOf("1万", "0.75万", "0.5万", "0.25万", "0万"), scale.ticks.map(scale::label))
        assertEquals("1亿", TrendChartScale(90_000_000.0).label(100_000_000.0))
    }

    @Test fun touchingDrawnEndpointsSelectsFirstAndLastDay() {
        assertEquals(0, trendDayIndex(8f, 300f, 8f, 31))
        assertEquals(30, trendDayIndex(292f, 300f, 8f, 31))
        assertEquals(0, trendDayIndex(-100f, 300f, 8f, 31))
        assertEquals(30, trendDayIndex(400f, 300f, 8f, 31))
    }

    @Test fun touchSelectsNearestDayInsteadOfAlwaysRoundingDown() {
        assertEquals(2, trendDayIndex(150f, 300f, 8f, 4))
        assertEquals(1, trendDayIndex(102f, 300f, 8f, 4))
    }

    @Test fun singleDayAndNarrowChartsRemainSelectable() {
        assertEquals(0, trendDayIndex(299f, 300f, 8f, 1))
        assertEquals(0, trendDayIndex(4f, 8f, 8f, 31))
    }
}
