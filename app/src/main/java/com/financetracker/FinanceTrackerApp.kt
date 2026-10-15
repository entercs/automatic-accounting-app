package com.financetracker

import android.app.Application
import androidx.room.withTransaction
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

        com.financetracker.notification.NotificationLogger.initialize(this)
        com.financetracker.notification.PaymentMonitor.refresh(this)

        // 注册全局无障碍服务状态监听器(1.0.4 新增)
        com.financetracker.notification.AccessibilityStateHolder.initialize(this)

        // Auto-initialize default data on first launch
        appScope.launch {
            database.withTransaction {
            com.financetracker.di.AppModule.categoryRepository.seedIfEmpty()
            com.financetracker.di.AppModule.paymentAccountRepository.seedIfEmpty()
            com.financetracker.di.AppModule.paymentAccountRepository.fixAccountNames()
            com.financetracker.di.AppModule.paymentAccountRepository.ensureBankAccount()
            }
        }
    }

    companion object {
        lateinit var instance: FinanceTrackerApp
            private set
    }
}
