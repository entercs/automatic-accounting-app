package com.financetracker.notification

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationLogEntry(
    val timestamp: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val parsed: Boolean,
    val parsedAmount: Double? = null,
    val outcome: String = if (parsed) "已记账" else "未识别",
    val source: String = "通知",
) {
    val formatted: String get() = "${SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(timestamp))} | $outcome\n来源: $source · $packageName\n标题: $title\n内容: $text\n${parsedAmount?.let { "金额: %.2f".format(it) }.orEmpty()}"
    val shortForm: String get() = "${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(timestamp))} $outcome $title"
}

/** Bounded, app-private diagnostic samples. Never uploaded; a user can copy or clear them. */
object NotificationLogger {
    private val entries = MutableStateFlow<List<NotificationLogEntry>>(emptyList())
    val logs = entries.asStateFlow()
    private var preferences: SharedPreferences? = null

    @Synchronized fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.getSharedPreferences("recognition_samples", Context.MODE_PRIVATE)
        entries.value = runCatching {
            val json = JSONArray(preferences!!.getString("entries", "[]"))
            (0 until json.length()).map { i ->
                val row = json.getJSONObject(i)
                NotificationLogEntry(row.getLong("time"), row.getString("package"), row.getString("title"),
                    row.getString("text"), row.getBoolean("parsed"),
                    if (row.has("amount")) row.getDouble("amount") else null,
                    row.getString("outcome"), row.getString("source"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized fun log(entry: NotificationLogEntry) {
        entries.value = (listOf(entry.copy(text = entry.text.take(2000))) + entries.value).take(100)
        persist()
    }

    fun getAll(): List<NotificationLogEntry> = entries.value

    @Synchronized fun clear() { entries.value = emptyList(); persist() }

    private fun persist() {
        val json = JSONArray()
        entries.value.forEach { e ->
            json.put(JSONObject().put("time", e.timestamp).put("package", e.packageName)
                .put("title", e.title).put("text", e.text).put("parsed", e.parsed)
                .put("amount", e.parsedAmount).put("outcome", e.outcome).put("source", e.source))
        }
        preferences?.edit()?.putString("entries", json.toString())?.apply()
    }
}
