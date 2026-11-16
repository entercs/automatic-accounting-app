package com.financetracker.ui.screen.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financetracker.data.preferences.AppPreferences
import com.financetracker.data.repository.CategoryRepository
import com.financetracker.data.repository.PaymentAccountRepository
import com.financetracker.data.repository.TransactionRepository
import com.financetracker.domain.calculator.Money
import com.financetracker.domain.model.BankTransfer
import com.financetracker.domain.model.ParsedNotification
import com.financetracker.domain.model.Category
import com.financetracker.domain.model.PaymentAccount
import com.financetracker.domain.model.Transaction
import com.financetracker.domain.model.TransactionType
import com.financetracker.notification.NotificationLogger
import com.financetracker.notification.NotificationLogEntry
import com.financetracker.notification.PaymentRecorder
import com.financetracker.notification.RecognitionApps
import com.financetracker.notification.RecognitionStatus
import com.financetracker.notification.parser.PaymentParsers
import com.financetracker.ui.component.KeypadKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

class AddTransactionViewModel(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: PaymentAccountRepository,
    private val prefs: AppPreferences,
    private val paymentRecorder: PaymentRecorder,
) : ViewModel() {

    val expenseCategories: StateFlow<List<Category>> =
        categoryRepo.getByType(TransactionType.EXPENSE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<Category>> =
        categoryRepo.getByType(TransactionType.INCOME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<PaymentAccount>> =
        accountRepo.getEnabled().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _selectedType = MutableStateFlow(TransactionType.EXPENSE)
    val selectedType: StateFlow<TransactionType> = _selectedType.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow(-1L)
    val selectedCategoryId: StateFlow<Long> = _selectedCategoryId.asStateFlow()

    private val _selectedAccountId = MutableStateFlow(-1L)
    val selectedAccountId: StateFlow<Long> = _selectedAccountId.asStateFlow()

    private val _transferToAccountId = MutableStateFlow(-1L)
    val transferToAccountId: StateFlow<Long> = _transferToAccountId.asStateFlow()

    private val _merchant = MutableStateFlow("")
    val merchant: StateFlow<String> = _merchant.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _date = MutableStateFlow(System.currentTimeMillis())
    val date: StateFlow<Long> = _date.asStateFlow()

    private val _reimbursable = MutableStateFlow(false)
    val reimbursable: StateFlow<Boolean> = _reimbursable.asStateFlow()

    private val _reimbursed = MutableStateFlow(false)
    val reimbursed: StateFlow<Boolean> = _reimbursed.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var saveTriggered = false

    private val _showEquals = MutableStateFlow(false)
    val showEquals: StateFlow<Boolean> = _showEquals.asStateFlow()

    private var editId: Long? = null
    private var originalSource = "manual"
    private var recognitionSample: NotificationLogEntry? = null
    private var parsedSample: ParsedNotification? = null

    private val _bankTransfer = MutableStateFlow<BankTransfer?>(null)
    val bankTransfer = _bankTransfer.asStateFlow()
    private val _rememberTransferAccounts = MutableStateFlow(true)
    val rememberTransferAccounts = _rememberTransferAccounts.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun setRememberTransferAccounts(remember: Boolean) { _rememberTransferAccounts.value = remember }
    fun dismissSaveError() { _saveError.value = null }

    init {
        viewModelScope.launch {
            val lastId = prefs.lastAccountId.first()
            val accs = accountRepo.getEnabled().first()
            if (_selectedAccountId.value > 0 || _bankTransfer.value != null) return@launch
            if (lastId > 0 && accs.any { it.id == lastId }) {
                _selectedAccountId.value = lastId
            } else if (accs.isNotEmpty()) {
                _selectedAccountId.value = accs.first().id
            }
        }
    }

    fun loadTransaction(id: Long) {
        editId = id
        viewModelScope.launch {
            val t = transactionRepo.getById(id) ?: return@launch
            _amount.value = Money.format(t.amount)
            _selectedType.value = t.type
            _selectedCategoryId.value = t.categoryId
            _selectedAccountId.value = t.accountId
            _transferToAccountId.value = t.transferToAccountId ?: -1L
            originalSource = t.source
            _merchant.value = t.merchant
            _note.value = t.note
            _date.value = t.date
            _reimbursable.value = t.reimbursable
            _reimbursed.value = t.reimbursed
        }
    }

    fun loadRecognitionSample(id: String) {
        if (recognitionSample?.id == id) return
        val sample = NotificationLogger.getAll().find { it.id == id } ?: return
        if (sample.status == RecognitionStatus.RECORDED || sample.status == RecognitionStatus.TRANSFER_CONFIRMATION) {
            _saveError.value = "这条通知已处理，请核对账本"
            return
        }
        val value = PaymentParsers.parse(sample.packageName, sample.title, sample.text)
        recognitionSample = sample
        parsedSample = value
        _bankTransfer.value = value?.bankTransfer
        _amount.value = (value?.amount ?: sample.parsedAmount)?.let { Money.format(it) }.orEmpty()
        _selectedType.value = value?.transactionType ?: sample.transactionType ?: TransactionType.EXPENSE
        _merchant.value = value?.merchant ?: sample.merchant.orEmpty()
        _date.value = sample.postedAt?.takeIf { it > 0 } ?: sample.timestamp
        val route = value?.bankTransfer
        _note.value = route?.purpose?.label ?: "识别补记：${sample.title}\n${sample.text.take(200)}"
        if (route != null) {
            _selectedAccountId.value = -1L
            _transferToAccountId.value = -1L
            viewModelScope.launch {
                paymentRecorder.confirmedBinding(route)?.let { binding ->
                    if (_selectedAccountId.value <= 0) _selectedAccountId.value = binding.fromAccountId
                    if (_transferToAccountId.value <= 0) _transferToAccountId.value = binding.toAccountId
                }
            }
            return
        }
        viewModelScope.launch {
            val appId = RecognitionApps.find(sample.packageName)?.id
            val type = value?.accountType ?: when (appId) {
                "wechat", "alipay", "jd" -> appId
                "meituan", "takeout", "dianping" -> "meituan"
                null -> null
                else -> "bank"
            }
            accountRepo.getEnabled().first().singleOrNull { it.type == type }?.let { _selectedAccountId.value = it.id }
        }
    }

    fun loadRefund(transactionId: Long) {
        viewModelScope.launch {
            val t = transactionRepo.getById(transactionId) ?: return@launch
            _amount.value = Money.format(t.amount)
            _selectedType.value = TransactionType.INCOME
            _selectedCategoryId.value = t.categoryId
            _selectedAccountId.value = t.accountId
            _merchant.value = "退款: ${t.merchant.ifBlank { "消费" }}"
            _note.value = "退款关联 #${t.id}"
            _date.value = System.currentTimeMillis()
            _reimbursable.value = false
        }
    }

    fun setAmount(value: String) {
        _amount.value = value
        updateShowEquals()
    }
    fun setType(type: TransactionType) {
        if (_bankTransfer.value != null) return
        _selectedType.value = type
        if (type != TransactionType.EXPENSE) { _reimbursable.value = false; _reimbursed.value = false }
        _selectedCategoryId.value = -1L
    }
    fun setCategoryId(id: Long) { _selectedCategoryId.value = id }
    fun setAccountId(id: Long) { _selectedAccountId.value = id }
    fun setTransferToAccountId(id: Long) { _transferToAccountId.value = id }
    fun setMerchant(value: String) { _merchant.value = value }
    fun setNote(value: String) { _note.value = value }
    fun setReimbursable(value: Boolean) { _reimbursable.value = value; if (!value) _reimbursed.value = false }
    fun setDate(value: Long) { _date.value = value }

    fun onKeypadKey(key: KeypadKey) {
        when (key) {
            KeypadKey.N0 -> appendChar('0')
            KeypadKey.N1 -> appendChar('1')
            KeypadKey.N2 -> appendChar('2')
            KeypadKey.N3 -> appendChar('3')
            KeypadKey.N4 -> appendChar('4')
            KeypadKey.N5 -> appendChar('5')
            KeypadKey.N6 -> appendChar('6')
            KeypadKey.N7 -> appendChar('7')
            KeypadKey.N8 -> appendChar('8')
            KeypadKey.N9 -> appendChar('9')
            KeypadKey.DOT -> appendDot()
            KeypadKey.BACKSPACE -> {
                if (_amount.value.isNotEmpty()) {
                    _amount.value = _amount.value.dropLast(1)
                }
                updateShowEquals()
            }
            KeypadKey.ADD -> appendOperator('+')
            KeypadKey.SUBTRACT -> appendOperator('-')
            KeypadKey.EQUALS -> evaluateAndShow()
            KeypadKey.SAVE -> { evaluateAndShow(); doSave() }
            KeypadKey.NEXT_RECORD -> {
                evaluateAndShow()
                doSave()
                _amount.value = ""
                _showEquals.value = false
            }
        }
    }

    private fun appendChar(d: Char) {
        val v = _amount.value
        val segment = v.substring(v.indexOfLast { it == '+' || it == '-' } + 1)
        if ('.' in segment && segment.substringAfter('.').length >= Money.SCALE) return
        if (v == "0") {
            _amount.value = d.toString()
        } else if (v.length < 15) {
            _amount.value = v + d
        }
        updateShowEquals()
    }

    private fun appendDot() {
        val v = _amount.value
        // Find the current "number segment" (since last operator)
        val lastOp = v.indexOfLast { it == '+' || it == '-' }
        val segment = if (lastOp >= 0) v.substring(lastOp + 1) else v
        if (!segment.contains('.') && segment.isNotEmpty() && segment.last().isDigit()) {
            _amount.value = v + '.'
        }
        updateShowEquals()
    }

    private fun appendOperator(op: Char) {
        val v = _amount.value
        if (v.isEmpty()) return
        val last = v.last()
        when {
            last == '+' || last == '-' -> _amount.value = v.dropLast(1) + op // replace operator
            last.isDigit() -> _amount.value = v + op
            // trailing dot, ignore
        }
        updateShowEquals()
        _showEquals.value = _amount.value.isNotEmpty() && _amount.value.last().isDigit() && _amount.value.any { it == '+' || it == '-' }
    }

    private fun updateShowEquals() {
        val v = _amount.value
        _showEquals.value = v.isNotEmpty() && v.last().isDigit() && v.any { it == '+' || it == '-' }
    }

    private fun evaluateAndShow() {
        val v = _amount.value
        if (v.isEmpty()) return
        val expr = if (v.last() in listOf('+', '-')) v.dropLast(1) else v
        if (expr.isEmpty()) return
        val result = evaluateExpression(expr) ?: return
        _amount.value = Money.format(result)
        _showEquals.value = false
    }

    private fun evaluateExpression(expr: String): BigDecimal? {
        var result = BigDecimal.ZERO
        var currentOp = '+'
        val numBuf = StringBuilder()
        for (ch in expr) {
            if (ch == '+' || ch == '-') {
                val num = numBuf.toString().toBigDecimalOrNull() ?: return null
                result = when (currentOp) {
                    '+' -> result.add(num)
                    '-' -> result.subtract(num)
                    else -> result
                }
                currentOp = ch
                numBuf.clear()
            } else {
                numBuf.append(ch)
            }
        }
        val num = numBuf.toString().toBigDecimalOrNull() ?: return null
        result = when (currentOp) {
            '+' -> result.add(num)
            '-' -> result.subtract(num)
            else -> result
        }
        return result
    }

    private fun doSave() {
        if (saveTriggered) return
        saveTriggered = true
        val expr = _amount.value
        // Strip trailing operator and evaluate if needed
        val cleanExpr = if (expr.isNotEmpty() && expr.last() in listOf('+', '-')) expr.dropLast(1) else expr
        val amt = if (cleanExpr.any { it == '+' || it == '-' }) {
            evaluateExpression(cleanExpr)?.let { Money.round(it).toDouble() } ?: run { saveTriggered = false; return }
        } else {
            cleanExpr.toBigDecimalOrNull()?.let { Money.round(it).toDouble() } ?: run { saveTriggered = false; return }
        }
        if (amt <= 0) { saveTriggered = false; return }
        val isTransfer = _selectedType.value == TransactionType.TRANSFER
        if (isTransfer && (_selectedAccountId.value <= 0 || _transferToAccountId.value <= 0 ||
                _transferToAccountId.value == _selectedAccountId.value)) {
            _saveError.value = "请选择不同的转出和转入账户"
            saveTriggered = false
            return
        }
        if (_selectedAccountId.value <= 0) { saveTriggered = false; return }

        val finalCategoryId: Long = if (!isTransfer && _selectedCategoryId.value <= 0) {
            val cats = if (_selectedType.value == TransactionType.INCOME) incomeCategories.value else expenseCategories.value
            cats.firstOrNull { it.name == "其他" }?.id
                ?: cats.firstOrNull()?.id
                ?: -1L
        } else {
            _selectedCategoryId.value
        }

        val transaction = Transaction(
            id = editId ?: 0,
            amount = amt,
            type = _selectedType.value,
            categoryId = if (isTransfer) -1L else finalCategoryId,
            accountId = _selectedAccountId.value,
            transferToAccountId = if (isTransfer) _transferToAccountId.value else null,
            merchant = _merchant.value,
            note = _note.value,
            reimbursable = _selectedType.value == TransactionType.EXPENSE && _reimbursable.value,
            reimbursed = _selectedType.value == TransactionType.EXPENSE && _reimbursable.value && _reimbursed.value,
            date = _date.value,
            source = originalSource,
        )
        val sample = recognitionSample
        val parsed = parsedSample
        val rememberAccounts = _rememberTransferAccounts.value
        viewModelScope.launch {
            try {
                if (sample != null && parsed?.bankTransfer != null) {
                    val now = System.currentTimeMillis()
                    val outcome = paymentRecorder.confirmTransfer(
                        parsed.copy(amount = transaction.amount, payTime = transaction.date, merchant = transaction.merchant),
                        sample.eventKey ?: "transfer-draft:${sample.id}", now, transaction.accountId,
                        requireNotNull(transaction.transferToAccountId), rememberAccounts, transaction.note,
                    )
                    if (outcome != "已记账" && outcome != "重复已跳过") {
                        _saveError.value = outcome
                        saveTriggered = false
                        return@launch
                    }
                    NotificationLogger.update(sample.id) {
                        it.copy(
                            parsed = true, parsedAmount = transaction.amount, merchant = transaction.merchant,
                            transactionType = TransactionType.TRANSFER, outcome = outcome, stage = "确认转账",
                            reason = if (outcome == "已记账") "已确认两端账户并保存转账" else "对应通知已入账",
                            processedAt = now, recordedAt = now.takeIf { outcome == "已记账" },
                        )
                    }
                } else if (editId != null) {
                    transactionRepo.update(transaction)
                } else {
                    transactionRepo.add(transaction)
                }
                _saved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveError.value = if (e is IllegalArgumentException) e.message ?: "账户不可用" else "保存失败，请重试"
                saveTriggered = false
                return@launch
            }
            prefs.setLastAccountId(transaction.accountId)
        }
    }

    class Factory(
        private val transactionRepo: TransactionRepository,
        private val categoryRepo: CategoryRepository,
        private val accountRepo: PaymentAccountRepository,
        private val prefs: AppPreferences,
        private val paymentRecorder: PaymentRecorder,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddTransactionViewModel(transactionRepo, categoryRepo, accountRepo, prefs, paymentRecorder) as T
        }
    }
}
