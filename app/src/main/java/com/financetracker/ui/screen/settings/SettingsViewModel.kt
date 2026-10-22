package com.financetracker.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financetracker.data.preferences.AppPreferences
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.notification.AccessibilityStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: PaymentAccountRepository,
    private val transactionRepo: TransactionRepository,
    private val context: Context,
) : ViewModel() {

    // 订阅全局无障碍状态(由 AccessibilityStateHolder 维护,实时反映系统设置变更)
    val isAccessibilityEnabled: StateFlow<Boolean> = AccessibilityStateHolder.isEnabled

    val accounts: StateFlow<List<PaymentAccount>> =
        accountRepo.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /** 打开系统无障碍设置页面 */
    fun openAccessibilitySettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() { _feedback.value = null }

    init {
        // Fix old default account names (one-time)
        viewModelScope.launch { accountRepo.fixAccountNames() }
    }

    fun seedCategories() {
        viewModelScope.launch {
            val before = categoryRepo.count()
            categoryRepo.seedIfEmpty()
            val after = categoryRepo.count()
            _feedback.value = if (after > before) "分类数据初始化成功" else "分类数据已存在，无需初始化"
        }
    }

    fun seedAccounts() {
        viewModelScope.launch {
            val before = accountRepo.count()
            accountRepo.seedIfEmpty()
            val after = accountRepo.count()
            _feedback.value = if (after > before) "账户数据初始化成功" else "账户数据已存在，无需初始化"
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            try {
                val transactions = transactionRepo.getRecent(10000).first()
                val file = File(context.cacheDir, "finance_export_${System.currentTimeMillis()}.csv")
                FileWriter(file).use { writer ->
                    writer.write("\uFEFF日期,类型,金额,分类ID,账户ID,商户,备注,来源,报销状态\n")
                    transactions.forEach { t ->
                        val status = when { !t.reimbursable || t.type != com.financetracker.domain.model.TransactionType.EXPENSE -> "无需报销"; t.reimbursed -> "已报销"; else -> "待报销" }
                        val fields = listOf(t.date.toString(), t.type.name, t.amount.toString(), t.categoryId.toString(), t.accountId.toString(), t.merchant, t.note, t.source, status)
                        writer.write(fields.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\n")
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                _exportState.value = ExportState.Success(uri)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "导出失败")
            }
        }
    }

    fun setBalance(accountId: Long, newBalance: Double) {
        viewModelScope.launch { accountRepo.updateBalance(accountId, newBalance) }
    }

    fun updateAccountName(accountId: Long, name: String, color: String) {
        viewModelScope.launch { accountRepo.updateNameAndColor(accountId, name, color) }
    }

    fun updateAccount(accountId: Long, name: String, color: String, balance: Double) {
        viewModelScope.launch {
            accountRepo.updateDetails(accountId, name, color, balance)
        }
    }

    fun createAccount(name: String, type: String, color: String, balance: Double) {
        viewModelScope.launch { accountRepo.create(name, type, color, balance) }
    }

    fun deleteAccount(account: PaymentAccount) {
        viewModelScope.launch {
            val count = transactionRepo.countByAccountId(account.id)
            if (count > 0) {
                _feedback.value = "「${account.name}」有 ${count} 笔交易记录，无法删除"
            } else {
                accountRepo.delete(account.id)
                _feedback.value = "「${account.name}」已删除"
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    sealed class ExportState {
        data object Idle : ExportState()
        data object Exporting : ExportState()
        data class Success(val uri: Uri) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    class Factory(
        private val prefs: AppPreferences,
        private val categoryRepo: CategoryRepository,
        private val accountRepo: PaymentAccountRepository,
        private val transactionRepo: TransactionRepository,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(prefs, categoryRepo, accountRepo, transactionRepo, context) as T
        }
    }
}
