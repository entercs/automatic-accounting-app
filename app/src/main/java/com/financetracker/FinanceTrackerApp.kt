package com.financetracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FinanceTrackerApp : Application() {
    lateinit var database: com.financetracker.data.db.AppDatabase
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = com.financetracker.data.db.AppDatabase.create(this)

        // Auto-initialize default data on first launch
        appScope.launch {
            com.financetracker.di.AppModule.categoryRepository.seedIfEmpty()
            com.financetracker.di.AppModule.paymentAccountRepository.seedIfEmpty()
            com.financetracker.di.AppModule.paymentAccountRepository.fixAccountNames()
            com.financetracker.di.AppModule.paymentAccountRepository.ensureBankAccount()
        }
    }

    companion object {
        lateinit var instance: FinanceTrackerApp
            private set
    }
}
