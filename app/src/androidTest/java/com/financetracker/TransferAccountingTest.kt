package com.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.financetracker.data.db.AppDatabase
import com.financetracker.data.db.entity.PaymentAccountEntity
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.PaymentRecorder
import com.financetracker.notification.parser.PaymentParsers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferAccountingTest {
    private lateinit var db: AppDatabase

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After fun closeDatabase() { db.close() }

    private val now = System.currentTimeMillis()
    private val wallet = PaymentParsers.parse(
        "cmb.pb", "数字人民币充钱包扣款",
        "您尾号2741的银行卡于11月16日18:26支出人民币490.54元，用于充值网商银行（支付宝）数字人民币钱包",
    )!!.copy(payTime = now - 1_000)
    private val repayment = PaymentParsers.parse(
        "cmb.pb", "快捷支付扣款",
        "您账户2741于11月16日18:26在【网银在线-京东白条】发生快捷支付扣款，人民币248.66",
    )!!.copy(payTime = now - 1_000)

    @Test fun confirmationsApplyBothBalancesOnceAndRememberAccountsPerRoute() = runBlocking {
        val accounts = db.paymentAccountDao()
        val bank = accounts.insert(PaymentAccountEntity(name = "招行尾号 2741", type = "bank", balance = 2000.0))
        val other = accounts.insert(PaymentAccountEntity(name = "其他银行卡", type = "bank", balance = 500.0))
        val walletId = accounts.insert(PaymentAccountEntity(name = "数字人民币钱包", type = "dcep", balance = 30.0))
        val jd = accounts.insert(PaymentAccountEntity(name = "京东", type = "jd", balance = -600.0))
        val recorder = PaymentRecorder(db)
        assertTrue(recorder.record(wallet, "wallet-1", now).startsWith("待补记"))
        assertTrue(recorder.record(repayment, "jd-1", now).startsWith("待补记"))
        assertFalse(db.accountingReceiptDao().exists("wallet-1"))
        assertEquals(0, db.transactionDao().count())
        assertEquals(2000.0, accounts.getById(bank)!!.balance, 0.0)
        assertEquals("已记账", recorder.confirmTransfer(wallet, "wallet-1", now, bank, walletId, true, "充值"))
        assertEquals("已记账", recorder.confirmTransfer(repayment, "jd-1", now, bank, jd, true, "白条还款"))
        assertEquals("重复已跳过", recorder.record(wallet, "wallet-1", now))
        assertEquals("重复已跳过", recorder.confirmTransfer(wallet, "wallet-1", now, other, walletId, true, "重复"))
        val transactions = TransactionRepository(db)
        val first = transactions.getById(1)!!
        val second = transactions.getById(2)!!
        assertEquals(walletId, first.transferToAccountId)
        assertEquals(jd, second.transferToAccountId)
        assertEquals(TransactionType.TRANSFER, first.type)
        assertEquals(TransactionType.TRANSFER, second.type)
        assertEquals(wallet.payTime, first.date)
        assertEquals(1260.8, accounts.getById(bank)!!.balance, .001)
        assertEquals(520.54, accounts.getById(walletId)!!.balance, .001)
        assertEquals(-351.34, accounts.getById(jd)!!.balance, .001)
        assertEquals(500.0, accounts.getById(other)!!.balance, 0.0)
        assertEquals(0.0, transactions.getMonthlyTotal(TransactionType.INCOME, 0, now + 1), 0.0)
        assertEquals(0.0, transactions.getMonthlyTotal(TransactionType.EXPENSE, 0, now + 1), 0.0)

        val firstDelivery = async { PaymentRecorder(db).record(wallet, "wallet-2", now) }
        val secondDelivery = async { recorder.record(wallet, "wallet-2", now) }
        assertEquals(setOf("已记账", "重复已跳过"), setOf(firstDelivery.await(), secondDelivery.await()))
        assertEquals("已记账", recorder.record(wallet, "wallet-3", now))
        assertEquals(4, transactions.getCount())
        assertEquals(279.72, accounts.getById(bank)!!.balance, .001)
    }

    @Test fun missingOrWrongTransferAccountsLeaveLedgerUntouched() = runBlocking {
        val accounts = db.paymentAccountDao()
        val bank = accounts.insert(PaymentAccountEntity(name = "招行", type = "bank", balance = 700.0))
        val alipay = accounts.insert(PaymentAccountEntity(name = "支付宝", type = "alipay"))
        val disabled = accounts.insert(PaymentAccountEntity(name = "旧数字人民币钱包", type = "dcep", isEnabled = false))
        val recorder = PaymentRecorder(db)
        assertTrue(recorder.confirmTransfer(wallet, "wallet", now, bank, alipay, true, "").startsWith("待补记"))
        assertTrue(recorder.confirmTransfer(wallet, "wallet", now, bank, disabled, true, "").startsWith("待补记"))
        assertFalse(db.accountingReceiptDao().exists("wallet"))
        assertNull(recorder.confirmedBinding(wallet.bankTransfer!!))
        assertEquals(700.0, accounts.getById(bank)!!.balance, 0.0)
        assertEquals(0, db.transactionDao().count())
    }

    @Test fun editingExpenseToTransferAndDeletingApplyOnlyTheRightBalanceChanges() = runBlocking {
        val accounts = db.paymentAccountDao()
        val bank = accounts.insert(PaymentAccountEntity(name = "招行", type = "bank", balance = 1000.0))
        val jd = accounts.insert(PaymentAccountEntity(name = "京东账户", type = "jd", balance = -400.0))
        val repository = TransactionRepository(db)
        val expense = Transaction(
            amount = 248.66, type = TransactionType.EXPENSE, categoryId = 1, accountId = bank,
            merchant = "网银在线-京东白条", date = now, source = "notification",
        )
        val id = repository.add(expense)
        val transfer = expense.copy(id = id, type = TransactionType.TRANSFER, categoryId = -1, transferToAccountId = jd)
        repository.update(transfer)

        assertEquals(transfer, repository.getById(id))
        assertEquals(1, repository.getCount())
        assertEquals(751.34, accounts.getById(bank)!!.balance, .001)
        assertEquals(-151.34, accounts.getById(jd)!!.balance, .001)
        assertEquals(0.0, repository.getMonthlyTotal(TransactionType.EXPENSE, 0, now + 1), 0.0)
        assertEquals(1, repository.countByAccountId(jd))
        assertEquals(0, accounts.deleteById(jd))
        assertEquals(listOf(transfer), repository.filter("京东账户", emptySet(), com.financetracker.domain.model.ReimbursementFilter.ALL).first())

        repository.delete(transfer)
        repository.delete(transfer)
        assertEquals(1000.0, accounts.getById(bank)!!.balance, .001)
        assertEquals(-400.0, accounts.getById(jd)!!.balance, .001)
    }
}
