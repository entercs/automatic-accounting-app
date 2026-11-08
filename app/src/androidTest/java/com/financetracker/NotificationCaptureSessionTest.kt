package com.financetracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financetracker.notification.NotificationCaptureSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCaptureSessionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun capturePeriodSurvivesProcessStyleReopening() {
        val name = "test_capture_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            assertEquals(1_000L, NotificationCaptureSession(prefs) { 1_000 }.update(true)!!)
            assertEquals(1_000L, NotificationCaptureSession(prefs) { 10_000 }.update(true)!!)
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun revokingAndReenablingAccessBeginsANewCapturePeriod() {
        val name = "test_capture_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            NotificationCaptureSession(prefs) { 1_000 }.update(true)
            assertNull(NotificationCaptureSession(prefs) { 2_000 }.update(false))
            assertEquals(3_000L, NotificationCaptureSession(prefs) { 3_000 }.update(true)!!)
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun upgradingWithoutSessionMetadataProtectsPreUpgradeHistory() {
        val name = "test_capture_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            assertNull(NotificationCaptureSession(prefs) { 1_000 }.update(false))
            assertEquals(5_000L, NotificationCaptureSession(prefs) { 5_000 }.update(true)!!)
        } finally { context.deleteSharedPreferences(name) }
    }
}
