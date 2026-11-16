package com.financetracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financetracker.data.db.AppDatabase
import com.financetracker.data.db.entity.CategoryEntity
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.PaymentRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticCategoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun newAutomaticExpensesAndIncomeUseDedicatedCategoryWithoutChangingDirectionOrBalance() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            val recorder = PaymentRecorder(db)
            assertEquals("已记账", recorder.record(ParsedNotification(16.90, "抖音", "alipay", TransactionType.EXPENSE, now), "auto-expense", now))
            assertEquals("已记账", recorder.record(ParsedNotification(3.0, "退款", "alipay", TransactionType.INCOME, now), "auto-income", now))
            val expense = db.transactionDao().getById(1)!!
            val income = db.transactionDao().getById(2)!!
            assertEquals("自动记账", db.categoryDao().getById(expense.categoryId)!!.name)
            assertEquals("EXPENSE", db.categoryDao().getById(expense.categoryId)!!.type)
            assertEquals("自动记账", db.categoryDao().getById(income.categoryId)!!.name)
            assertEquals("INCOME", income.type)
            assertEquals("INCOME", db.categoryDao().getById(income.categoryId)!!.type)
            assertNotEquals(expense.categoryId, income.categoryId)
            assertEquals(-13.90, db.paymentAccountDao().getByType("alipay")!!.balance, .001)
        } finally { db.close() }
    }
    @Test fun addingCategoriesLeavesExistingAutomaticAndManualRecordsUntouched() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.categoryDao().insert(CategoryEntity(8, "其他支出", "📂", "EXPENSE"))
            PaymentAccountRepository(db.paymentAccountDao()).seedIfEmpty()
            val repo = TransactionRepository(db)
            val auto = repo.add(Transaction(amount = 1.0, type = TransactionType.EXPENSE, categoryId = 8, accountId = 1, merchant = "旧自动", date = 1234L, source = "notification"))
            val manual = repo.add(Transaction(amount = 2.0, type = TransactionType.EXPENSE, categoryId = 8, accountId = 1, merchant = "手动", date = 2345L))
            val before = db.transactionDao().getAll().first()
            val balances = db.paymentAccountDao().getAll().first()
            CategoryRepository(db.categoryDao()).seedIfEmpty()
            assertEquals(before, db.transactionDao().getAll().first())
            assertEquals(balances, db.paymentAccountDao().getAll().first())
            assertEquals(8L, db.transactionDao().getById(auto)!!.categoryId)
            assertEquals(8L, db.transactionDao().getById(manual)!!.categoryId)
        } finally { db.close() }
    }
    @Test fun customIdsAreNotOverwrittenAndRepeatedSeedingDoesNotDuplicateAutomaticCategories() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.categoryDao().insert(CategoryEntity(12, "自定义分类", "⭐", "EXPENSE"))
            val repo = CategoryRepository(db.categoryDao())
            repeat(3) { repo.seedIfEmpty() }
            assertEquals("自定义分类", db.categoryDao().getById(12)!!.name)
            assertEquals(2, db.categoryDao().getAll().first().count { it.name == "自动记账" })
            val ids = coroutineScope { (1..6).map { async(Dispatchers.IO) { repo.automaticCategoryId(TransactionType.EXPENSE) } }.awaitAll() }
            assertEquals(1, ids.distinct().size)
        } finally { db.close() }
    }
    @Test fun douyinBankConsumptionDebitsOnlyTheBankAccount() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val value = com.financetracker.notification.parser.PaymentParsers.parse("cmb.pb", "消费通知",
                "您的银行卡消费人民币16.90元，商户：【抖音商城-订单123456】")!!
            assertEquals("已记账", PaymentRecorder(db).record(value, "bank-douyin", System.currentTimeMillis()))
            val tx = db.transactionDao().getById(1)!!
            val account = db.paymentAccountDao().getById(tx.accountId)!!
            assertEquals("抖音", tx.merchant)
            assertEquals("bank", account.type)
            assertEquals(-16.90, account.balance, .001)
            assertEquals(0.0, db.paymentAccountDao().getByType("alipay")!!.balance, .001)
            assertEquals(0.0, db.paymentAccountDao().getByType("wechat")!!.balance, .001)
            assertEquals("自动记账", db.categoryDao().getById(tx.categoryId)!!.name)
            assertFalse(tx.note.contains("抖音支付"))
        } finally { db.close() }
    }

}
