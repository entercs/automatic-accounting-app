package com.financetracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DateTimeSelectionTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test fun localDateAndTimeSurviveReopening() {
        val selection = DateTimeSelection(2026, 10, 22, 0, 15)
        assertEquals(selection, DateTimeSelection.fromEpochMillis(selection.toEpochMillis(shanghai), shanghai))
    }

    @Test fun importedTimeUsesLocalCalendarRatherThanUtcDay() {
        val timestamp = java.time.Instant.parse("2026-10-21T16:15:37.123Z").toEpochMilli()
        assertEquals(DateTimeSelection(2026, 10, 22, 0, 15), DateTimeSelection.fromEpochMillis(timestamp, shanghai))
    }

    @Test fun changingMonthClampsDayAndPreservesTime() {
        assertEquals(DateTimeSelection(2026, 4, 30, 23, 59), DateTimeSelection(2026, 1, 31, 23, 59).withMonth(4))
    }

    @Test fun leapYearFebruaryHasTwentyNineDays() {
        assertEquals(DateTimeSelection(2024, 2, 29, 8, 7), DateTimeSelection(2024, 1, 31, 8, 7).withMonth(2))
    }

    @Test fun changingLeapYearClampsFebruaryDay() {
        assertEquals(DateTimeSelection(2025, 2, 28, 8, 7), DateTimeSelection(2024, 2, 29, 8, 7).withYear(2025))
    }

    @Test fun minuteSelectionNormalizesSecondsAndMillis() {
        val input = LocalDateTime.of(2026, 10, 22, 14, 36, 59, 999_000_000).atZone(shanghai).toInstant().toEpochMilli()
        val actual = DateTimeSelection.fromEpochMillis(input, shanghai).toEpochMillis(shanghai)
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 36).atZone(shanghai).toInstant().toEpochMilli(), actual)
    }

    @Test fun negativeUtcOffsetAlsoPreservesChosenDayAndMinute() {
        val zone = ZoneId.of("America/Los_Angeles")
        val selection = DateTimeSelection(2026, 12, 31, 23, 59)
        assertEquals(selection, DateTimeSelection.fromEpochMillis(selection.toEpochMillis(zone), zone))
    }
}
