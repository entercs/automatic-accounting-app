package com.financetracker.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Shared currency policy for display, editor values, calculation results and exports. */
object Money {
    const val SCALE = 2

    fun round(value: BigDecimal): BigDecimal = value.setScale(SCALE, RoundingMode.HALF_UP)

    fun format(value: Double, grouping: Boolean = false): String =
        format(BigDecimal.valueOf(value), grouping)

    fun format(value: BigDecimal, grouping: Boolean = false): String {
        val amount = round(value)
        if (!grouping) return amount.toPlainString()
        // DecimalFormat is mutable; each call owns its formatter, including background exports.
        return DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.CHINA)).format(amount)
    }
}
