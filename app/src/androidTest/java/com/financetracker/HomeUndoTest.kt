package com.financetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financetracker.data.db.AppDatabase
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import com.financetracker.ui.screen.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeUndoTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun model(db: AppDatabase, repo: TransactionRepository, duration: Long): Pair<HomeViewModel, ViewModelStore> {
        val store = ViewModelStore()
        lateinit var vm: HomeViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo, CategoryRepository(db.categoryDao()), PaymentAccountRepository(db.paymentAccountDao()), duration) as T
            })[HomeViewModel::class.java]
        }
        return vm to store
    }
    private suspend fun seed(db: AppDatabase, repo: TransactionRepository, amount: Double = 20.0): Transaction {
        PaymentAccountRepository(db.paymentAccountDao()).seedIfEmpty()
        val id = repo.add(Transaction(amount = amount, type = TransactionType.EXPENSE, categoryId = 1, accountId = 1, merchant = "撤销测试", date = System.currentTimeMillis()))
        return repo.getById(id)!!
    }
    @Test fun opportunityExpiresWithoutAnySnackbarOrVisibleScreen() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var store: ViewModelStore? = null
        try {
            val repo = TransactionRepository(db.transactionDao(), db.paymentAccountDao())
            val tx = seed(db, repo)
            val (vm, ownedStore) = model(db, repo, 300); store = ownedStore
            instrumentation.runOnMainSync { vm.deleteTransaction(tx) }
            val opportunity = withTimeout(5000) { vm.pendingUndo.first { it != null }!! }
            delay(500)
            assertNull(vm.pendingUndo.value)
            instrumentation.runOnMainSync { vm.undoDelete(opportunity.token) }
            delay(50)
            assertNull(repo.getById(tx.id))
            assertEquals(0.0, db.paymentAccountDao().getById(1)!!.balance, .001)
        } finally { instrumentation.runOnMainSync { store?.clear() }; db.close() }
    }
    @Test fun doubleUndoRestoresOriginalIdAndBalanceOnlyOnce() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var store: ViewModelStore? = null
        try {
            val repo = TransactionRepository(db.transactionDao(), db.paymentAccountDao())
            val tx = seed(db, repo)
            val (vm, ownedStore) = model(db, repo, 10000); store = ownedStore
            instrumentation.runOnMainSync { vm.deleteTransaction(tx) }
            val opportunity = withTimeout(5000) { vm.pendingUndo.first { it != null }!! }
            instrumentation.runOnMainSync { vm.undoDelete(opportunity.token); vm.undoDelete(opportunity.token) }
            withTimeout(5000) { db.paymentAccountDao().getAll().first { it.first { a -> a.id == 1L }.balance == -20.0 } }
            assertEquals(tx.id, repo.getById(tx.id)!!.id)
            assertEquals(1, repo.getCount()); assertNull(vm.pendingUndo.value)
        } finally { instrumentation.runOnMainSync { store?.clear() }; db.close() }
    }
    @Test fun staleSnackbarCannotCancelExpiryForTheNextDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var store: ViewModelStore? = null
        try {
            val repo = TransactionRepository(db.transactionDao(), db.paymentAccountDao())
            val first = seed(db, repo); val second = seed(db, repo, 30.0)
            val (vm, ownedStore) = model(db, repo, 500); store = ownedStore
            instrumentation.runOnMainSync { vm.deleteTransaction(first) }
            val old = withTimeout(5000) { vm.pendingUndo.first { it != null }!! }
            instrumentation.runOnMainSync { vm.deleteTransaction(second) }
            withTimeout(5000) { vm.pendingUndo.first { it?.value?.id == second.id } }
            instrumentation.runOnMainSync { vm.dismissUndo(old.token); vm.undoDelete(old.token) }
            delay(700)
            assertNull(vm.pendingUndo.value); assertEquals(0, repo.getCount())
            assertEquals(0.0, db.paymentAccountDao().getById(1)!!.balance, .001)
        } finally { instrumentation.runOnMainSync { store?.clear() }; db.close() }
    }
    @Test fun repeatedDeleteDoesNotAdjustTheBalanceTwice() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var store: ViewModelStore? = null
        try {
            val repo = TransactionRepository(db.transactionDao(), db.paymentAccountDao())
            val tx = seed(db, repo)
            val (vm, ownedStore) = model(db, repo, 10000); store = ownedStore
            instrumentation.runOnMainSync { vm.deleteTransaction(tx); vm.deleteTransaction(tx) }
            withTimeout(5000) { vm.pendingUndo.first { it != null } }
            delay(100)
            assertEquals(0, repo.getCount()); assertEquals(0.0, db.paymentAccountDao().getById(1)!!.balance, .001)
        } finally { instrumentation.runOnMainSync { store?.clear() }; db.close() }
    }
}
