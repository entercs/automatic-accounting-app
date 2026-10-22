package com.financetracker.ui.component

import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Local calendar fields for a transaction, with minute precision. */
data class DateTimeSelection(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    val daysInMonth: Int get() = YearMonth.of(year, month).lengthOfMonth()

    fun withYear(value: Int) = copy(year = value, day = day.coerceAtMost(YearMonth.of(value, month).lengthOfMonth()))
    fun withMonth(value: Int) = copy(month = value, day = day.coerceAtMost(YearMonth.of(year, value).lengthOfMonth()))

    fun toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    companion object {
        fun fromEpochMillis(value: Long, zone: ZoneId = ZoneId.systemDefault()): DateTimeSelection {
            val local = Instant.ofEpochMilli(value).atZone(zone)
            return DateTimeSelection(local.year, local.monthValue, local.dayOfMonth, local.hour, local.minute)
        }
    }
}
