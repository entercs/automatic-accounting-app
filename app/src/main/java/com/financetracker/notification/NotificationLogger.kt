package com.financetracker.notification

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.financetracker.domain.model.TransactionType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Compatible with the V1.2.x samples; a malformed row never discards the other records. */
object RecognitionCodec {
    fun decode(raw: String): List<NotificationLogEntry> {
        val json = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until json.length()).mapNotNull { i -> runCatching {
            val row = json.getJSONObject(i)
            val originalOutcome = row.optString("outcome", "未识别")
            val interrupted = originalOutcome == "处理中"
            NotificationLogEntry(
                timestamp = row.getLong("time"), packageName = row.getString("package"),
                title = row.optString("title"), text = row.optString("text"), parsed = row.optBoolean("parsed"),
                parsedAmount = if (row.has("amount") && !row.isNull("amount")) row.getDouble("amount") else null,
                outcome = if (interrupted) "结果待核对" else originalOutcome,
                source = row.optString("source", "通知读取").let { if (it == "通知") "通知读取" else it },
                id = row.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                reason = if (interrupted) "上次处理未完成或结果尚未保存，请先核对账本，避免重复补记" else row.optString("reason"),
                stage = row.optString("stage", "历史记录"),
                postedAt = if (row.has("postedAt") && !row.isNull("postedAt")) row.getLong("postedAt") else null,
                merchant = row.optString("merchant").ifBlank { null },
                transactionType = runCatching { TransactionType.valueOf(row.optString("type")) }.getOrNull(),
                paymentMethod = row.optString("method").ifBlank { null },
                processingStartedAt = row.optionalTime("processingStartedAt"),
                processedAt = row.optionalTime("processedAt"),
                recordedAt = row.optionalTime("recordedAt"),
                reminderRequestedAt = row.optionalTime("reminderRequestedAt"),
                reminderPostedAt = row.optionalTime("reminderPostedAt"),
                reminderOutcome = row.optString("reminderOutcome").ifBlank { null },
                eventKey = row.optString("eventKey").ifBlank { null },
            )
        }.getOrNull() }
    }

    fun encode(entries: List<NotificationLogEntry>): String = JSONArray().apply {
        entries.forEach { e -> put(JSONObject().put("id", e.id).put("time", e.timestamp).put("package", e.packageName)
            .put("title", e.title).put("text", e.text).put("parsed", e.parsed).put("amount", e.parsedAmount)
            .put("outcome", e.outcome).put("source", e.source).put("reason", e.reason).put("stage", e.stage)
            .put("postedAt", e.postedAt).put("merchant", e.merchant).put("type", e.transactionType?.name).put("method", e.paymentMethod)
            .put("processingStartedAt", e.processingStartedAt).put("processedAt", e.processedAt).put("recordedAt", e.recordedAt)
            .put("reminderRequestedAt", e.reminderRequestedAt).put("reminderPostedAt", e.reminderPostedAt).put("reminderOutcome", e.reminderOutcome).put("eventKey", e.eventKey)) }
    }.toString()

    private fun JSONObject.optionalTime(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
}

/** Small bounded local store. Serial IO always persists the latest state, including a clear during processing. */
class RecognitionLogStore(private val preferences: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow(RecognitionRetention.keep(RecognitionCodec.decode(preferences.getString("entries", "[]").orEmpty()), now()))
    val logs = state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { for (request in requests) flush() }
        persist()
    }

    @Synchronized fun log(entry: NotificationLogEntry) {
        state.value = RecognitionRetention.keep(listOf(entry.copy(text = entry.text.take(2000))) + state.value.filterNot { it.id == entry.id }, now())
        persist()
    }
    @Synchronized fun update(id: String, change: (NotificationLogEntry) -> NotificationLogEntry) {
        if (state.value.none { it.id == id }) return // Clearing records must not resurrect in-flight entries.
        state.value = RecognitionRetention.keep(state.value.map { if (it.id == id) change(it).let { updated -> updated.copy(text = updated.text.take(2000)) } else it }, now())
        persist()
    }
    @Synchronized fun refresh() { state.value = RecognitionRetention.keep(state.value, now()); persist() }
    @Synchronized fun clear() { state.value = emptyList(); persist() }
    // Awaitable IO is required by persistence tests and the single writer.
    @SuppressLint("ApplySharedPref")
    suspend fun flush() = writes.withLock { preferences.edit().putString("entries", RecognitionCodec.encode(state.value)).commit(); Unit }
    fun close() { requests.close(); scope.cancel() }
    private fun persist() { requests.trySend(Unit) }
}

object NotificationLogger {
    private var store: RecognitionLogStore? = null
    val logs get() = requireNotNull(store) { "Recognition logger is not initialized" }.logs
    @Synchronized fun initialize(context: Context) {
        if (store == null) store = RecognitionLogStore(context.getSharedPreferences("recognition_samples", Context.MODE_PRIVATE))
    }
    fun log(entry: NotificationLogEntry) { store?.log(entry) }
    fun update(id: String, change: (NotificationLogEntry) -> NotificationLogEntry) { store?.update(id, change) }
    fun refresh() { store?.refresh() }
    fun getAll(): List<NotificationLogEntry> = store?.logs?.value.orEmpty()
    fun clear() { store?.clear() }
    fun runtime(title: String, reason: String) {
        log(NotificationLogEntry(System.currentTimeMillis(), NotificationLogEntry.SERVICE_PACKAGE,
            title, "", false, outcome = "服务动态", source = "服务状态", reason = reason, stage = "后台运行"))
    }
    fun service(connected: Boolean, reason: String) {
        log(NotificationLogEntry(System.currentTimeMillis(), NotificationLogEntry.SERVICE_PACKAGE,
            if (connected) "通知监听已连接" else "通知监听已断开", "", false,
            outcome = "服务动态", source = "服务状态", reason = reason, stage = "连接状态"))
    }
}
