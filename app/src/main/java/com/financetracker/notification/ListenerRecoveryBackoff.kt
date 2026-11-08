package com.financetracker.notification

/** Monotonic retry deadlines prevent resume/callback/guardian requests from creating a busy loop. */
class ListenerRecoveryBackoff {
    private val delays = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)
    var attempts = 0
        private set
    private var nextAttemptAt = 0L

    fun remainingMillis(now: Long): Long = (nextAttemptAt - now).coerceAtLeast(0)

    fun requested(now: Long) {
        nextAttemptAt = now + delays[attempts.coerceAtMost(delays.lastIndex)]
        attempts++
    }

    fun reset() {
        attempts = 0
        nextAttemptAt = 0
    }
}
