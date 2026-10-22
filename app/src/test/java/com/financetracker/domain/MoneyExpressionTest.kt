package com.financetracker.domain

import com.financetracker.domain.calculator.MoneyExpression
import org.junit.Assert.*
import org.junit.Test

class MoneyExpressionTest {
    private fun assertValue(input: String, expected: String) = assertEquals(expected, MoneyExpression.evaluate(input)?.toPlainString())
    @Test fun decimalAdditionIsExact() = assertValue("0.1+0.2", "0.30")
    @Test fun chainedAdditionAndSubtraction() = assertValue("100+23.50-10", "113.50")
    @Test fun negativeBalancesAndFurtherSubtraction() = assertValue("-100-20+5.50", "-114.50")
    @Test fun incompleteAndMalformedAmountsCannotBeSaved() {
        listOf("", "-", "100+", "100-", "1+-2", "1.2.3", "1.234", "1e3", "1/2").forEach { assertNull(it, MoneyExpression.evaluate(it)) }
    }
    @Test fun excessiveBalancesAreRejected() { assertNull(MoneyExpression.evaluate("999999999999.99+0.01")) }
    @Test fun operatorsCanBeReplaced() { assertEquals("100-", MoneyExpression.append("100+", "-")) }
    @Test fun decimalsArePerOperand() {
        assertEquals("1.20+0.", MoneyExpression.append("1.20+", "."))
        assertEquals("1.20+0.5", MoneyExpression.append("1.20+0.5", "."))
        assertEquals("1.20+0.50", MoneyExpression.append("1.20+0.50", "1"))
    }
    @Test fun digitStartsAValueWithoutExtraLeadingZero() { assertEquals("20-5", MoneyExpression.append("20-0", "5")) }
    @Test fun negativeInputCanStartWithMinus() { assertEquals("-", MoneyExpression.append("", "-")); assertValue("-.50+1", "0.50") }
}
