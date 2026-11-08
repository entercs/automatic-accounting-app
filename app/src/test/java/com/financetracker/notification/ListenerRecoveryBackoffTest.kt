package com.financetracker.notification

import org.junit.Assert.*
import org.junit.Test

class ListenerRecoveryBackoffTest {
    @Test fun firstRequestIsImmediateAndRepeatedCallbacksCannotBypassDeadline() {
        val backoff = ListenerRecoveryBackoff()
        assertEquals(0L, backoff.remainingMillis(10_000))
        backoff.requested(10_000)
        assertEquals(2_000L, backoff.remainingMillis(10_000))
        assertEquals(1L, backoff.remainingMillis(11_999))
        assertEquals(0L, backoff.remainingMillis(12_000))
        assertEquals(1, backoff.attempts)
    }

    @Test fun continuingFailuresBackOffAndRemainCapped() {
        val backoff = ListenerRecoveryBackoff()
        var now = 1_000L
        listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L, 60_000L).forEach { expected ->
            backoff.requested(now)
            assertEquals(expected, backoff.remainingMillis(now))
            now += expected
        }
    }

    @Test fun successfulConnectionOrRevokedAuthorizationClearsOldFailureDelay() {
        val backoff = ListenerRecoveryBackoff()
        repeat(5) { backoff.requested(10_000) }
        backoff.reset()
        assertEquals(0, backoff.attempts)
        assertEquals(0L, backoff.remainingMillis(10_001))
        backoff.requested(10_001)
        assertEquals(2_000L, backoff.remainingMillis(10_001))
    }
}
