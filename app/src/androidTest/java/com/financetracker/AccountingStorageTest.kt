package com.financetracker

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financetracker.data.db.AppDatabase
import com.financetracker.data.db.entity.PaymentAccountEntity
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.PaymentRecorder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun payment(type: TransactionType = TransactionType.EXPENSE) = ParsedNotification(20.0, "回归测试商店", "wechat", type, System.currentTimeMillis())

    @Test fun duplicateEventsAndEqualAmountsHaveCorrectBalanceEffect() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val recorder = PaymentRecorder(db)
            val now = System.currentTimeMillis()
            assertEquals("已记账", recorder.record(payment(), "first", now))
            assertEquals("重复已跳过", recorder.record(payment(), "first", now))
            assertEquals("已记账", recorder.record(payment(), "second", now))
            assertEquals(2, db.transactionDao().count())
            assertEquals(-40.0, db.paymentAccountDao().getByType("wechat")!!.balance, .001)
            assertEquals("已记账", recorder.record(payment(TransactionType.INCOME), "refund", now))
            assertEquals(-20.0, db.paymentAccountDao().getByType("wechat")!!.balance, .001)
        } finally { db.close() }
    }

    @Test fun dailyTotalsUseTheDevicesLocalMidnight() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            PaymentRecorder(db).record(payment(), "daily", now)
            val midnight = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
            val totals = db.transactionDao().getDailyTotals("EXPENSE", midnight, now + 1000)
            assertEquals(1, totals.size)
            assertEquals(midnight, totals.single().dayStart)
            assertEquals(20.0, totals.single().total, .001)
        } finally { db.close() }
    }

    @Test fun receiptSurvivesReopen() = runBlocking {
        val name = "test_receipt_${System.nanoTime()}.db"
        try {
            var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val now = System.currentTimeMillis()
            assertEquals("已记账", PaymentRecorder(db).record(payment(), "persistent", now))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals("重复已跳过", PaymentRecorder(db).record(payment(), "persistent", now))
            assertEquals(1, db.transactionDao().count())
            assertEquals(-20.0, db.paymentAccountDao().getByType("wechat")!!.balance, .001)
            db.close()
        } finally { context.deleteDatabase(name) }
    }

    @Test fun ambiguousAccountsAreKeptPending() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.paymentAccountDao().insert(PaymentAccountEntity(name = "微信一", type = "wechat"))
            db.paymentAccountDao().insert(PaymentAccountEntity(name = "微信二", type = "wechat"))
            assertTrue(PaymentRecorder(db).record(payment(), "ambiguous", System.currentTimeMillis()).startsWith("待补记"))
            assertEquals(0, db.transactionDao().count())
            assertFalse(db.accountingReceiptDao().exists("ambiguous"))
        } finally { db.close() }
    }

    @Test fun upgradingV6PreservesExistingLedgerAndBalances() = runBlocking {
        val name = "test_migration_${System.nanoTime()}.db"
        val template = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // Recreate the unchanged v6 tables from the actual Room schema, excluding only the new receipt table.
            val statements = mutableListOf<String>()
            template.openHelper.writableDatabase.query("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT IN ('accounting_receipts','room_master_table','android_metadata','sqlite_sequence') AND name NOT LIKE 'sqlite_%'").use { cursor ->
                while (cursor.moveToNext()) statements.add(cursor.getString(0))
            }
            context.getDatabasePath(name).parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
                statements.map { it.replace(", `reimbursed` INTEGER NOT NULL DEFAULT 0", "") }.forEach(old::execSQL)
                old.execSQL("INSERT INTO payment_accounts (id,name,type,isEnabled,color,balance) VALUES (1,'原有微信','wechat',1,'#07C160',880.0)")
                old.execSQL("INSERT INTO transactions (id,amount,type,categoryId,accountId,transferToAccountId,merchant,note,reimbursable,date,source) VALUES (1,20.0,'EXPENSE',8,1,NULL,'原有账单','',0,1000,'manual')")
                old.version = 6
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).build()
            try {
                assertEquals("原有账单", migrated.transactionDao().getById(1)!!.merchant)
                assertEquals(880.0, migrated.paymentAccountDao().getById(1)!!.balance, .001)
                assertFalse(migrated.accountingReceiptDao().exists("new"))
            } finally { migrated.close() }
        } finally { template.close(); context.deleteDatabase(name) }
    }
}
