package com.financetracker.domain

import com.financetracker.domain.calculator.Money
import com.financetracker.domain.calculator.MoneyExpression
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun everyCompletedAmountHasTwoDecimals() {
        assertEquals("0.00", Money.format(0.0))
        assertEquals("12.00", Money.format(12.0))
        assertEquals("12.30", Money.format(12.3))
        assertEquals("-12.30", Money.format(-12.3))
    }

    @Test fun floatingPointTailBecomesAnEditableAndSaveableBalance() {
        val editorValue = Money.format(0.1 + 0.2)
        assertEquals("0.30", editorValue)
        assertEquals(BigDecimal("0.30"), MoneyExpression.evaluate(editorValue))
        assertEquals(BigDecimal("0.20"), MoneyExpression.evaluate(MoneyExpression.append(editorValue, "-") + "0.1"))
    }

    @Test fun roundingUsesHalfUpForBothSigns() {
        assertEquals("12.35", Money.format(BigDecimal("12.345")))
        assertEquals("-12.35", Money.format(BigDecimal("-12.345")))
        assertEquals("100.00", Money.format(BigDecimal("99.995")))
    }

    @Test fun negativeFloatingPointResidueDoesNotDisplayNegativeZero() {
        assertEquals("0.00", Money.format(0.3 - 0.2 - 0.1))
        assertEquals("0.00", Money.format(-0.004))
    }

    @Test fun groupedOverviewAndPlainEditorShowTheSameAmount() {
        assertEquals("1,234,567.90", Money.format(1234567.899999999, grouping = true))
        assertEquals("1234567.90", Money.format(1234567.899999999))
        assertEquals("-1,234.50", Money.format(-1234.5, grouping = true))
    }

    @Test fun decimalSeparatorIsStableAcrossDeviceLocales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1234.50", Money.format(1234.5))
            assertEquals("1,234.50", Money.format(1234.5, grouping = true))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun balanceResultsAndFollowingOperandsStayAtCentPrecision() {
        val result = MoneyExpression.evaluate("0.1+0.2")!!
        assertEquals("0.30", Money.format(result))
        assertEquals("0.30+0.12", MoneyExpression.append("0.30+0.12", "3"))
        assertNull(MoneyExpression.evaluate("0.30+0.123"))
    }

    @Test fun maximumSupportedBalanceKeepsItsCents() {
        assertEquals("999999999999.99", Money.format(999999999999.99))
    }
}
