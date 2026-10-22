package com.financetracker.domain.undo

/** A single-use opportunity measured by an injected monotonic clock, independent of any screen. */
data class UndoOpportunity<T>(val token: Long, val value: T, val expiresAtMillis: Long)

class UndoWindow<T>(private val durationMillis: Long, private val now: () -> Long) {
    init { require(durationMillis > 0) }
    private var sequence = 0L
    private var current: UndoOpportunity<T>? = null

    @Synchronized fun offer(value: T): UndoOpportunity<T> = UndoOpportunity(++sequence, value, now() + durationMillis).also { current = it }

    @Synchronized fun peek(): UndoOpportunity<T>? {
        if (current?.let { now() >= it.expiresAtMillis } == true) current = null
        return current
    }

    @Synchronized fun consume(token: Long): T? {
        val opportunity = peek()?.takeIf { it.token == token } ?: return null
        current = null
        return opportunity.value
    }

    @Synchronized fun dismiss(token: Long): Boolean {
        if (peek()?.token != token) return false
        current = null
        return true
    }
}
