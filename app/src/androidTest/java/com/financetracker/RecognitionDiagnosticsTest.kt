package com.financetracker

import android.app.Notification
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.*
import com.financetracker.notification.parser.PaymentParsers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionDiagnosticsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun record(now: Long) = NotificationLogEntry(now, "com.eg.android.AlipayGphone", "交易提醒", "你有一笔16.90元的支出", false, outcome = "处理中")

    @Test fun legacyRowsIncludingNullAmountRemainReadable() {
        val now = System.currentTimeMillis()
        val rows = RecognitionCodec.decode("""[{"time":$now,"package":"com.eg.android.AlipayGphone","title":"交易提醒","text":"旧样本","parsed":false,"amount":null,"outcome":"未识别","source":"通知"}]""")
        assertEquals(1, rows.size)
        assertEquals("通知读取", rows.single().source)
        assertEquals("旧样本", rows.single().text)
        assertNull(rows.single().parsedAmount)
    }
    @Test fun malformedRowDoesNotDiscardOtherRows() {
        val valid = RecognitionCodec.encode(listOf(record(System.currentTimeMillis()).copy(outcome = "未识别")))
        val result = RecognitionCodec.decode(valid.dropLast(1) + ",{}]")
        assertEquals(1, result.size)
    }
    @Test fun richerFieldsRoundTripWithoutChangingIdentityOrOriginalTimes() {
        val item = record(System.currentTimeMillis()).copy(outcome = "已记账", parsed = true, parsedAmount = 16.90,
            postedAt = 1234L, reason = "已保存", stage = "入账", merchant = "商户", paymentMethod = "花呗", transactionType = TransactionType.EXPENSE)
        assertEquals(item, RecognitionCodec.decode(RecognitionCodec.encode(listOf(item))).single())
    }
    @Test fun interruptedCaptureIsMarkedForReconciliationRatherThanReplayed() {
        val item = RecognitionCodec.decode(RecognitionCodec.encode(listOf(record(System.currentTimeMillis())))).single()
        assertEquals("结果待核对", item.outcome)
        assertTrue(item.reason.contains("核对账本"))
    }
    @Test fun processingUpdatesOneRecordAndSurvivesReopening() = runBlocking {
        val name = "test_recognition_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        var store: RecognitionLogStore? = null
        try {
            val time = System.currentTimeMillis()
            store = RecognitionLogStore(prefs)
            val item = record(time)
            store.log(item)
            store.update(item.id) { it.copy(outcome = "已记账", parsed = true, parsedAmount = 16.90, reason = "已保存", stage = "入账") }
            store.flush()
            assertEquals(1, store.logs.value.size)
            assertEquals(time, store.logs.value.single().timestamp)
            store.close()
            store = RecognitionLogStore(prefs)
            assertEquals("已记账", store.logs.value.single().outcome)
            assertEquals(item.id, store.logs.value.single().id)
        } finally { store?.close(); context.deleteSharedPreferences(name) }
    }
    @Test fun clearingDuringProcessingCannotResurrectDeletedSamples() = runBlocking {
        val name = "test_recognition_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val store = RecognitionLogStore(prefs)
        try {
            val item = record(System.currentTimeMillis())
            store.log(item); store.clear(); store.update(item.id) { it.copy(outcome = "已记账") }; store.flush()
            assertTrue(store.logs.value.isEmpty())
            assertTrue(RecognitionCodec.decode(prefs.getString("entries", "[]")!!).isEmpty())
        } finally { store.close(); context.deleteSharedPreferences(name) }
    }
    @Test fun oldRecordsArePrunedWhenOpeningStore() {
        val name = "test_recognition_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit().putString("entries", RecognitionCodec.encode(listOf(record(now - RecognitionRetention.MAX_AGE - 1), record(now).copy(outcome = "已记账")))).commit()
        val store = RecognitionLogStore(prefs) { now }
        try { assertEquals(listOf(now), store.logs.value.map { it.timestamp }) }
        finally { store.close(); context.deleteSharedPreferences(name) }
    }
    @Test fun ordinaryTextIsNotLostWhenExpandedTextOnlyContainsPromotion() {
        val notification = Notification.Builder(context, "test").setContentTitle("交易提醒").setContentText("你有一笔16.90元的支出")
            .setStyle(Notification.BigTextStyle().bigText("点击领取2元流量红包")).build()
        val payload = NotificationPayload.read(notification)
        assertTrue(payload.body.contains("点击领取"))
        assertTrue(payload.body.contains("16.90"))
        assertEquals(16.90, PaymentParsers.parse("com.eg.android.AlipayGphone", payload.title, payload.body)!!.amount, .001)
    }
    @Test fun duplicateStandardAndExpandedTextAreKeptOnce() {
        val text = "你有一笔16.90元的支出"
        val notification = Notification.Builder(context, "test").setContentTitle("交易提醒").setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).build()
        assertEquals(text, NotificationPayload.read(notification).body)
    }
    @Test fun samplePreviewCannotWriteLedgerOrChangeBalances() = runBlocking {
        val db = FinanceTrackerApp.instance.database
        val before = db.transactionDao().count()
        val accountsBefore = com.financetracker.data.repository.PaymentAccountRepository(db.paymentAccountDao()).getAll().first()
        repeat(3) { assertNotNull(PaymentParsers.parse("com.eg.android.AlipayGphone", "交易提醒", "你有一笔16.90元的支出，2元红包待领取。")) }
        assertEquals(before, db.transactionDao().count())
        val accountsAfter = com.financetracker.data.repository.PaymentAccountRepository(db.paymentAccountDao()).getAll().first()
        assertEquals(accountsBefore, accountsAfter)
    }
}
