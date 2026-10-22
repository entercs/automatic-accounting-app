package com.financetracker.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/** Small decimal calculator for account balances, including negative balances. */
object MoneyExpression {
    fun evaluate(expression: String): BigDecimal? {
        val input = expression.replace('−', '-').replace(" ", "")
        if (input.isEmpty() || input.length > 80) return null
        val number = "(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})"
        if (!Regex("[+-]?$number(?:[+-]$number)*").matches(input)) return null
        val tokens = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})").findAll(input).toList()
        if (tokens.isEmpty() || tokens.joinToString("") { it.value } != input) return null
        return tokens.fold(BigDecimal.ZERO) { total, token -> total + token.value.toBigDecimal() }
            .setScale(2, RoundingMode.HALF_UP)
            .takeIf { it.abs() <= BigDecimal("999999999999.99") }
    }

    fun append(expression: String, key: String): String {
        if (key == "+" || key == "-") {
            if (expression.isEmpty()) return if (key == "-") "-" else ""
            return if (expression.last() == '+' || expression.last() == '-') expression.dropLast(1) + key
                else if (expression.last().isDigit()) expression + key else expression
        }
        if (expression.length >= 64) return expression
        val start = expression.indexOfLast { it == '+' || it == '-' } + 1
        val segment = expression.substring(start)
        if (key == ".") return if ('.' in segment) expression else expression + if (segment.isEmpty()) "0." else "."
        if (key.length != 1 || !key[0].isDigit()) return expression
        if (segment.substringAfter('.', "").length >= 2 && '.' in segment) return expression
        if (segment.substringBefore('.').length >= 12 && '.' !in segment) return expression
        return if (segment == "0") expression.dropLast(1) + key else expression + key
    }
}
