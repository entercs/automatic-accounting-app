package com.financetracker.notification

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/** Persist across process death, but start a new capture period after access is revoked/re-enabled. */
class NotificationCaptureSession(private val preferences: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    @SuppressLint("ApplySharedPref")
    @Synchronized fun update(authorized: Boolean): Long? {
        if (!authorized) {
            if (preferences.getBoolean("capture_authorized", false) || preferences.contains("capture_started_at")) {
                preferences.edit().putBoolean("capture_authorized", false).remove("capture_started_at").commit()
            }
            return null
        }
        val previous = preferences.getLong("capture_started_at", 0)
        if (preferences.getBoolean("capture_authorized", false) && previous > 0) return previous
        val startedAt = now()
        preferences.edit().putBoolean("capture_authorized", true).putLong("capture_started_at", startedAt).commit()
        return startedAt
    }

    companion object {
        fun preferences(context: Context): SharedPreferences = context.getSharedPreferences("accounting_runtime", Context.MODE_PRIVATE)
        fun forContext(context: Context) = NotificationCaptureSession(preferences(context))
    }
}
