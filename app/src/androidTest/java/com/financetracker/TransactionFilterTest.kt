package com.financetracker

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financetracker.data.db.AppDatabase
import com.financetracker.data.db.entity.TransactionEntity
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.*
import com.financetracker.notification.ScreenCapturePolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionFilterTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun seeded(db: AppDatabase): TransactionRepository {
        CategoryRepository(db.categoryDao()).seedIfEmpty()
        PaymentAccountRepository(db.paymentAccountDao()).seedIfEmpty()
        val repo = TransactionRepository(db.transactionDao(), db.paymentAccountDao())
        repo.add(Transaction(amount = 10.0, type = TransactionType.EXPENSE, categoryId = 1, accountId = 1, merchant = "公司午餐", note = "项目A", reimbursable = true, date = 1000))
        repo.add(Transaction(amount = 20.0, type = TransactionType.EXPENSE, categoryId = 2, accountId = 1, merchant = "文具", note = "项目A", reimbursable = true, reimbursed = true, date = 2000))
        repo.add(Transaction(amount = 5.0, type = TransactionType.EXPENSE, categoryId = 1, accountId = 1, merchant = "咖啡", date = 3000))
        repo.add(Transaction(amount = 100.0, type = TransactionType.INCOME, categoryId = 9, accountId = 1, merchant = "工资", reimbursable = true, date = 4000))
        return repo
    }
    @Test fun categoryAndPendingFiltersIntersectWithKeywords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = seeded(db)
            val rows = repo.filter("项目A", setOf(1), ReimbursementFilter.PENDING).first()
            assertEquals(listOf("公司午餐"), rows.map { it.merchant })
            assertTrue(repo.filter("项目A", setOf(2), ReimbursementFilter.PENDING).first().isEmpty())
            assertEquals(3, repo.filter("", setOf(1, 2), ReimbursementFilter.ALL).first().size)
            assertEquals(1, repo.filter("", emptySet(), ReimbursementFilter.PENDING).first().size)
            assertEquals(listOf("文具"), repo.filter("", emptySet(), ReimbursementFilter.REIMBURSED).first().map { it.merchant })
        } finally { db.close() }
    }
    @Test fun emptySearchAndCategoryFilterIncludeRecordsBeyondLatestThirty() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            PaymentAccountRepository(db.paymentAccountDao()).seedIfEmpty()
            repeat(35) { i -> db.transactionDao().insert(TransactionEntity(amount = 1.0, type = "EXPENSE", categoryId = if (i == 0) 2 else 1, accountId = 1, merchant = "账单$i", date = i.toLong())) }
            val repo = TransactionRepository(db.transactionDao(), db.paymentAccountDao())
            assertEquals(35, repo.filter("", emptySet(), ReimbursementFilter.ALL).first().size)
            assertEquals("账单0", repo.filter("", setOf(2), ReimbursementFilter.ALL).first().single().merchant)
        } finally { db.close() }
    }
    @Test fun togglingReimbursementChangesOnlyStatusAndUpdatesFilters() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = seeded(db)
            val pending = repo.filter("", emptySet(), ReimbursementFilter.PENDING).first().single()
            val balance = db.paymentAccountDao().getById(1)!!.balance
            val count = db.transactionDao().count()
            repo.setReimbursed(pending.id, true)
            assertTrue(repo.getById(pending.id)!!.reimbursed)
            assertTrue(repo.filter("", emptySet(), ReimbursementFilter.PENDING).first().isEmpty())
            assertEquals(2, repo.filter("", emptySet(), ReimbursementFilter.REIMBURSED).first().size)
            assertEquals(balance, db.paymentAccountDao().getById(1)!!.balance, .001)
            assertEquals(count, db.transactionDao().count())
            repo.setReimbursed(pending.id, false)
            assertEquals(pending.id, repo.filter("", emptySet(), ReimbursementFilter.PENDING).first().single().id)
            val normal = repo.filter("咖啡", emptySet(), ReimbursementFilter.ALL).first().single()
            repo.setReimbursed(normal.id, true)
            assertFalse(repo.getById(normal.id)!!.reimbursed)
        } finally { db.close() }
    }
    @Test fun queryMatchesAccountNamesAndCategoryNames() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = seeded(db)
            assertEquals(4, repo.filter("微信", emptySet(), ReimbursementFilter.ALL).first().size)
            assertEquals(2, repo.filter("餐饮", emptySet(), ReimbursementFilter.ALL).first().size)
            assertEquals(1, repo.filter(" 午餐 ", emptySet(), ReimbursementFilter.ALL).first().size)
        } finally { db.close() }
    }
    @Test fun v7MigrationPreservesPendingFlagsAmountsBalancesAndEventReceipts() = runBlocking {
        val name = "test_v7_${System.nanoTime()}.db"
        val template = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val schema = mutableListOf<String>()
            template.openHelper.writableDatabase.query("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT IN ('room_master_table','android_metadata','sqlite_sequence') AND name NOT LIKE 'sqlite_%'").use { c -> while (c.moveToNext()) schema.add(c.getString(0).replace(", `reimbursed` INTEGER NOT NULL DEFAULT 0", "")) }
            assertTrue(schema.none { "`reimbursed`" in it })
            context.getDatabasePath(name).parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
                schema.forEach(old::execSQL)
                old.execSQL("INSERT INTO payment_accounts (id,name,type,isEnabled,color,balance) VALUES (1,'微信','wechat',1,'#07C160',880.0)")
                old.execSQL("INSERT INTO transactions (id,amount,type,categoryId,accountId,transferToAccountId,merchant,note,reimbursable,date,source) VALUES (1,20.0,'EXPENSE',1,1,NULL,'原有待报销','',1,1000,'manual')")
                old.execSQL("INSERT INTO accounting_receipts (eventId,createdAt) VALUES ('legacy',1000)")
                old.version = 7
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_7_8).build()
            try {
                val row = db.transactionDao().getById(1)!!
                assertTrue(row.reimbursable); assertFalse(row.reimbursed); assertEquals(20.0, row.amount, .001)
                assertEquals(880.0, db.paymentAccountDao().getById(1)!!.balance, .001)
                assertTrue(db.accountingReceiptDao().exists("legacy"))
            } finally { db.close() }
        } finally { template.close(); context.deleteDatabase(name) }
    }
    @Test fun paymentRoutesCombineWithKeywordsCategoriesAndReimbursement() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = seeded(db)
            repo.add(Transaction(amount = 7.0, type = TransactionType.EXPENSE, categoryId = 1, accountId = 2, merchant = "公司午餐", reimbursable = true, date = 5000))
            repo.add(Transaction(amount = 8.0, type = TransactionType.EXPENSE, categoryId = 2, accountId = 3, merchant = "银行卡购物", date = 6000))
            val bank = PaymentAccountRepository(db.paymentAccountDao()).create("另一张银行卡", "bank", "#B98849", 0.0)
            repo.add(Transaction(amount = 9.0, type = TransactionType.EXPENSE, categoryId = 1, accountId = bank, merchant = "银行卡餐饮", reimbursable = true, date = 7000))
            assertEquals(4, repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("wechat")).first().size)
            assertEquals(5, repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("wechat", "alipay")).first().size)
            assertEquals(2, repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("bank")).first().size)
            assertEquals(7.0, repo.filter("午餐", setOf(1), ReimbursementFilter.PENDING, setOf("alipay")).first().single().amount, .001)
            assertEquals(9.0, repo.filter("", setOf(1), ReimbursementFilter.PENDING, setOf("bank")).first().single().amount, .001)
            assertTrue(repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("jd")).first().isEmpty())
        } finally { db.close() }
    }
    @Test fun transfersMatchEitherRouteWithoutDuplicateRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            PaymentAccountRepository(db.paymentAccountDao()).seedIfEmpty()
            val repo = TransactionRepository(db.transactionDao(), db.paymentAccountDao())
            repo.add(Transaction(amount = 10.0, type = TransactionType.TRANSFER, categoryId = -1, accountId = 1, transferToAccountId = 2, date = 1000))
            assertEquals(1, repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("wechat")).first().size)
            assertEquals(1, repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("alipay")).first().size)
            assertEquals(1, repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("wechat", "alipay")).first().size)
            assertTrue(repo.filter("", emptySet(), ReimbursementFilter.PENDING, setOf("wechat")).first().isEmpty())
            assertTrue(repo.filter("", emptySet(), ReimbursementFilter.ALL, setOf("bank")).first().isEmpty())
        } finally { db.close() }
    }

    @Test fun systemAndRuntimeScreenWhitelistsMatch() {
        val xml = context.resources.getXml(R.xml.accessibility_service_config)
        try {
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) xml.next()
            val values = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "packageNames").split(',').map { it.trim() }.toSet()
            assertEquals(ScreenCapturePolicy.packages, values)
        } finally { xml.close() }
    }
}
