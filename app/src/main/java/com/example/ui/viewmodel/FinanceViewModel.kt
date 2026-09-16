package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.repository.FinanceRepository
import com.example.data.repository.BackupRepository
import android.net.Uri
import com.example.service.PushNotificationHelper
import com.example.service.UserBankHelper
import com.example.service.UserFinancePreferences
import com.example.service.AppThemeMode
import com.example.service.gemini.*
import com.example.ui.util.CurrencyHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class CategorySpending(
    val category: CategoryEntity,
    val totalAmount: Double,
    val percentage: Float
)

data class BudgetProgress(
    val budget: BudgetEntity,
    val category: CategoryEntity?,
    val spent: Double,
    val limit: Double,
    val percent: Float,
    val isOverBudget: Boolean
)

data class FinanceUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val plannedTransactions: List<PlannedTransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val pendingNotifications: List<PendingNotificationEntity> = emptyList(),
    val baseCurrency: String = "RUB",
    val totalBalance: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val categorySpendings: List<CategorySpending> = emptyList(),
    val budgetProgresses: List<BudgetProgress> = emptyList(),
    val totalOwedToMe: Double = 0.0,
    val totalIOwe: Double = 0.0,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isAiConfigured: Boolean = false,
    val userGeminiApiKey: String = "",
    val geminiApiKeyMasked: String = "",
    val aiMessages: List<AiMessage> = emptyList(),
    val aiInputText: String = "",
    val aiState: AiState = AiState.Idle,
    val payday: Int = 10,
    val paydayPeriodLabel: String = "",
    val daysUntilPayday: Int = 0,
    val dayOfCycle: Int = 1,
    val totalDaysInCycle: Int = 30,
    val bankOfTheMonth: String = "VTB",
    val isPushNotificationsEnabled: Boolean = true,
    val isBankPushInterceptEnabled: Boolean = true,
    val isZenmoneyPushInterceptEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isFirstLaunch: Boolean = false
)

@Suppress("UNCHECKED_CAST")

fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> combine10(
    flow1: Flow<T1>, flow2: Flow<T2>, flow3: Flow<T3>, flow4: Flow<T4>, flow5: Flow<T5>,
    flow6: Flow<T6>, flow7: Flow<T7>, flow8: Flow<T8>, flow9: Flow<T9>, flow10: Flow<T10>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow1, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10
) { args: Array<*> ->
    transform(
        args[0] as T1, args[1] as T2, args[2] as T3, args[3] as T4, args[4] as T5,
        args[5] as T6, args[6] as T7, args[7] as T8, args[8] as T9, args[9] as T10
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> combine9(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow1, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9
    )
}

data class AppConfig(
    val baseCurrency: String,
    val payday: Int,
    val bankOfTheMonth: String,
    val isPushNotificationsEnabled: Boolean,
    val isBankPushInterceptEnabled: Boolean,
    val isZenmoneyPushInterceptEnabled: Boolean,
    val themeMode: AppThemeMode
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    val backupRepository: BackupRepository

    private val repository: FinanceRepository
    val geminiPrefs = GeminiPreferenceManager(application)
    val geminiService = GeminiService(geminiPrefs)
    val userFinancePrefs = UserFinancePreferences(application)

    private val _baseCurrency = MutableStateFlow("RUB")
    val baseCurrency: StateFlow<String> = _baseCurrency.asStateFlow()

    private val _payday = MutableStateFlow(userFinancePrefs.getPaydayDay())
    val payday: StateFlow<Int> = _payday.asStateFlow()

    private val _bankOfTheMonth = MutableStateFlow(userFinancePrefs.getBankOfTheMonth())
    val bankOfTheMonth: StateFlow<String> = _bankOfTheMonth.asStateFlow()

    private val _isPushNotificationsEnabled = MutableStateFlow(userFinancePrefs.isPushNotificationsEnabled())
    val isPushNotificationsEnabled: StateFlow<Boolean> = _isPushNotificationsEnabled.asStateFlow()

    private val _isBankPushInterceptEnabled = MutableStateFlow(userFinancePrefs.isBankPushInterceptEnabled())
    private val _isZenmoneyPushInterceptEnabled = MutableStateFlow(userFinancePrefs.isZenmoneyPushInterceptEnabled())

    private val _themeMode = MutableStateFlow(userFinancePrefs.getThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val appConfigFlow = combine(
        _baseCurrency,
        _payday,
        _bankOfTheMonth,
        combine(
            _isPushNotificationsEnabled,
            _isBankPushInterceptEnabled,
            _isZenmoneyPushInterceptEnabled,
            _themeMode
        ) { pushEnabled, bankIntercept, zenmoneyIntercept, theme ->
            listOf(pushEnabled, bankIntercept, zenmoneyIntercept, theme)
        }
    ) { curr, payday, bank, extras ->
        AppConfig(
            baseCurrency = curr,
            payday = payday,
            bankOfTheMonth = bank,
            isPushNotificationsEnabled = extras[0] as Boolean,
            isBankPushInterceptEnabled = extras[1] as Boolean,
            isZenmoneyPushInterceptEnabled = extras[2] as Boolean,
            themeMode = extras[3] as AppThemeMode
        )
    }

    private val _statusMessage = MutableStateFlow<String?>(null)

    private val _aiMessages = MutableStateFlow<List<AiMessage>>(emptyList())
    private val _aiInputText = MutableStateFlow("")
    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    private val _userApiKey = MutableStateFlow(geminiPrefs.getUserApiKey())
    private val _isAiConfigured = MutableStateFlow(geminiPrefs.isApiKeyConfigured())

    private val _isEveningSummaryEnabled = MutableStateFlow(userFinancePrefs.isEveningSummaryEnabled())
    val isEveningSummaryEnabledFlow: StateFlow<Boolean> = _isEveningSummaryEnabled.asStateFlow()

    private val _eveningSummaryTime = MutableStateFlow(userFinancePrefs.getEveningSummaryTime())
    val eveningSummaryTimeFlow: StateFlow<String> = _eveningSummaryTime.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(database)
        backupRepository = BackupRepository(application, database.accountDao(), database.categoryDao(), database.transactionDao(), database.budgetDao(), database.goalDao(), database.debtDao(), database.plannedTransactionDao(), userFinancePrefs)
        
        // Restore evening summary schedule if enabled
        if (_isEveningSummaryEnabled.value) {
            com.example.service.EveningSummaryScheduler.schedule(getApplication(), _eveningSummaryTime.value, forceUpdate = false)
        }
    }

    private val baseFinanceFlow = combine10(
        repository.allAccounts,
        repository.allCategories,
        repository.allTransactions,
        repository.allPlannedTransactions,
        repository.allBudgets,
        repository.allGoals,
        repository.allDebts,
        repository.unprocessedNotifications,
        appConfigFlow,
        _statusMessage
    ) { accounts, categories, transactions, plannedTransactions, budgets, goals, debts, pendingNotifications, config, status ->
        // 1. Total Balance in base currency
        val activeAccounts = accounts.filter { !it.isArchived && it.includeInTotal }
        val totalBalance = activeAccounts.sumOf { acc ->
            CurrencyHelper.convert(acc.balance, acc.currency, config.baseCurrency)
        }

        // 2. Financial cycle based on Payday (starts on payday, ends day before next payday)
        val paydayPeriod = userFinancePrefs.calculatePaydayPeriod(config.payday)
        val thisMonthTransactions = transactions.filter {
            it.timestamp >= paydayPeriod.startTime && it.timestamp <= paydayPeriod.endTime && !it.excludeFromStats
        }

        val monthlyIncome = thisMonthTransactions.sumOf { tx ->
            val acc = accounts.find { it.id == tx.accountId }
            val toAcc = accounts.find { it.id == tx.toAccountId }
            val accInAnalytics = acc?.includeInAnalytics ?: true
            val toAccInAnalytics = toAcc?.includeInAnalytics ?: true
            
            val convertedAmount = CurrencyHelper.convert(tx.amount, acc?.currency ?: config.baseCurrency, config.baseCurrency)

            if (tx.type == "INCOME" && accInAnalytics) {
                convertedAmount
            } else if (tx.type == "TRANSFER" && !accInAnalytics && toAccInAnalytics) {
                convertedAmount
            } else {
                0.0
            }
        }

        val monthlyExpense = thisMonthTransactions.sumOf { tx ->
            val acc = accounts.find { it.id == tx.accountId }
            val toAcc = accounts.find { it.id == tx.toAccountId }
            val accInAnalytics = acc?.includeInAnalytics ?: true
            val toAccInAnalytics = toAcc?.includeInAnalytics ?: true
            
            val convertedAmount = CurrencyHelper.convert(tx.amount, acc?.currency ?: config.baseCurrency, config.baseCurrency)

            if (tx.type == "EXPENSE" && accInAnalytics) {
                convertedAmount
            } else if (tx.type == "TRANSFER" && accInAnalytics && !toAccInAnalytics) {
                convertedAmount
            } else {
                0.0
            }
        }

        // 3. Category spending breakdown
        val expenseTx = thisMonthTransactions.filter { it.type == "EXPENSE" }
        val catMap = categories.associateBy { it.id }
        val spendingByCat = mutableMapOf<Long, Double>()
        expenseTx.forEach { tx ->
            val catId = tx.categoryId ?: -1L
            val acc = accounts.find { it.id == tx.accountId }
            val curr = acc?.currency ?: config.baseCurrency
            val convertedAmount = CurrencyHelper.convert(tx.amount, curr, config.baseCurrency)
            spendingByCat[catId] = (spendingByCat[catId] ?: 0.0) + convertedAmount
        }

        val categorySpendings = spendingByCat.mapNotNull { (catId, amount) ->
            val cat = catMap[catId] ?: CategoryEntity(
                id = -1,
                name = "Без категории",
                type = "EXPENSE",
                iconName = "more_horiz",
                colorHex = "#94A3B8"
            )
            val pct = if (monthlyExpense > 0) (amount / monthlyExpense).toFloat() else 0f
            CategorySpending(cat, amount, pct)
        }.sortedByDescending { it.totalAmount }

        // 4. Budget progresses
        val budgetProgresses = budgets.map { budget ->
            val category = budget.categoryId?.let { catMap[it] }
            val spent = if (budget.categoryId == null) {
                monthlyExpense
            } else {
                spendingByCat[budget.categoryId] ?: 0.0
            }
            val percent = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
            BudgetProgress(
                budget = budget,
                category = category,
                spent = spent,
                limit = budget.limitAmount,
                percent = percent,
                isOverBudget = spent > budget.limitAmount
            )
        }

        // 5. Debts summary
        val activeDebts = debts.filter { !it.isSettled }
        val totalOwedToMe = activeDebts.filter { it.isOwedToMe }.sumOf { it.amount }
        val totalIOwe = activeDebts.filter { !it.isOwedToMe }.sumOf { it.amount }

        FinanceUiState(
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            plannedTransactions = plannedTransactions,
            budgets = budgets,
            goals = goals,
            debts = debts,
            pendingNotifications = pendingNotifications,
            baseCurrency = config.baseCurrency,
            totalBalance = totalBalance,
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            categorySpendings = categorySpendings,
            budgetProgresses = budgetProgresses,
            totalOwedToMe = totalOwedToMe,
            totalIOwe = totalIOwe,
            statusMessage = status,
            payday = config.payday,
            paydayPeriodLabel = paydayPeriod.periodLabel,
            daysUntilPayday = paydayPeriod.daysUntilPayday,
            dayOfCycle = paydayPeriod.dayOfCycle,
            totalDaysInCycle = paydayPeriod.totalDaysInCycle,
            bankOfTheMonth = config.bankOfTheMonth,
            isPushNotificationsEnabled = config.isPushNotificationsEnabled,
            isBankPushInterceptEnabled = config.isBankPushInterceptEnabled,
            isZenmoneyPushInterceptEnabled = config.isZenmoneyPushInterceptEnabled,
            themeMode = config.themeMode
        )
    }

    private val _isFirstLaunch = MutableStateFlow(userFinancePrefs.isFirstLaunch())

    val uiState: StateFlow<FinanceUiState> = combine(
        baseFinanceFlow,
        _aiMessages,
        _aiState,
        _isAiConfigured,
        _userApiKey,
        _isFirstLaunch,
        _aiInputText
    ) { params ->
        val baseState = params[0] as FinanceUiState
        val messages = params[1] as List<AiMessage>
        val aiState = params[2] as AiState
        val isAiConfigured = params[3] as Boolean
        val userKey = params[4] as String
        val isFirstLaunch = params[5] as Boolean
        val aiInputText = params[6] as String
        baseState.copy(
            aiMessages = messages,
            aiState = aiState,
            isAiConfigured = isAiConfigured,
            userGeminiApiKey = userKey,
            geminiApiKeyMasked = geminiPrefs.getMaskedApiKey(),
            isFirstLaunch = isFirstLaunch,
            aiInputText = aiInputText
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState(isLoading = true)
    )

    fun setThemeMode(mode: AppThemeMode) {
        userFinancePrefs.setThemeMode(mode)
        _themeMode.value = mode
    }

    fun setBaseCurrency(currency: String) {
        _baseCurrency.value = currency
    }

    fun addTransaction(
        type: String,
        amount: Double,
        accountId: Long,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
        note: String = "",
        tag: String = "",
        excludeFromStats: Boolean = false,
        goalId: Long? = null,
        debtId: Long? = null
    ) {
        viewModelScope.launch {
            repository.addTransaction(
                TransactionEntity(
                    type = type,
                    amount = amount,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    categoryId = categoryId,
                    timestamp = timestamp,
                    note = note,
                    tag = tag,
                    excludeFromStats = excludeFromStats,
                    goalId = goalId,
                    debtId = debtId
                )
            )
            _statusMessage.value = "Операция успешно добавлена"
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            _statusMessage.value = "Операция удалена"
        }
    }

    fun updateTransaction(
        oldTransaction: TransactionEntity,
        newTransaction: TransactionEntity
    ) {
        viewModelScope.launch {
            repository.updateTransaction(oldTransaction, newTransaction)
            _statusMessage.value = "Операция успешно обновлена"
        }
    }

    fun isEveningSummaryEnabled(): Boolean {
        return _isEveningSummaryEnabled.value
    }

    fun setEveningSummaryEnabled(enabled: Boolean) {
        userFinancePrefs.setEveningSummaryEnabled(enabled)
        _isEveningSummaryEnabled.value = enabled
        if (enabled) {
            com.example.service.EveningSummaryScheduler.schedule(getApplication(), _eveningSummaryTime.value, forceUpdate = true)
        } else {
            com.example.service.EveningSummaryScheduler.cancel(getApplication())
        }
    }

    fun getEveningSummaryTime(): String {
        return _eveningSummaryTime.value
    }

    fun setEveningSummaryTime(time: String) {
        userFinancePrefs.setEveningSummaryTime(time)
        _eveningSummaryTime.value = time
        if (_isEveningSummaryEnabled.value) {
            com.example.service.EveningSummaryScheduler.schedule(getApplication(), time, forceUpdate = true)
        }
    }

    fun setPushNotificationsEnabled(enabled: Boolean) {
        userFinancePrefs.setPushNotificationsEnabled(enabled)
        _isPushNotificationsEnabled.value = enabled
        _statusMessage.value = if (enabled) "Пуш-уведомления включены" else "Пуш-уведомления отключены"
    }

    fun setBankPushInterceptEnabled(enabled: Boolean) {
        userFinancePrefs.setBankPushInterceptEnabled(enabled)
        _isBankPushInterceptEnabled.value = enabled
    }

    fun setZenmoneyPushInterceptEnabled(enabled: Boolean) {
        userFinancePrefs.setZenmoneyPushInterceptEnabled(enabled)
        _isZenmoneyPushInterceptEnabled.value = enabled
    }

    fun sendTestPushNotification() {
        PushNotificationHelper.sendTestReminder(getApplication())
        _statusMessage.value = "Тестовое пуш-уведомление отправлено"
    }

    fun addAccount(
        name: String,
        type: String,
        initialBalance: Double,
        currency: String,
        colorHex: String,
        iconName: String,
        includeInTotal: Boolean,
        includeInAnalytics: Boolean
    ) {
        viewModelScope.launch {
            repository.insertAccount(
                AccountEntity(
                    name = name,
                    type = type,
                    balance = initialBalance,
                    currency = currency,
                    colorHex = colorHex,
                    iconName = iconName,
                    includeInTotal = includeInTotal,
                    includeInAnalytics = includeInAnalytics
                )
            )
            _statusMessage.value = "Счёт «$name» создан"
        }
    }

    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
            _statusMessage.value = "Счёт обновлён"
        }
    }

    fun archiveAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account.copy(isArchived = !account.isArchived))
            _statusMessage.value = if (!account.isArchived) "Счёт архивирован" else "Счёт восстановлен"
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
            _statusMessage.value = "Счёт удалён"
        }
    }

    fun updateAccountsOrder(reorderedAccounts: List<AccountEntity>) {
        viewModelScope.launch {
            val updatedAccounts = reorderedAccounts.mapIndexed { index, account ->
                account.copy(orderIndex = index)
            }
            repository.updateAccounts(updatedAccounts)
        }
    }

    fun updateCategory(category: com.example.data.entity.CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: com.example.data.entity.CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun addCategory(name: String, type: String, iconName: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertCategory(
                CategoryEntity(
                    name = name,
                    type = type,
                    iconName = iconName,
                    colorHex = colorHex
                )
            )
            _statusMessage.value = "Категория «$name» добавлена"
        }
    }

    fun addBudget(categoryId: Long?, limitAmount: Double) {
        viewModelScope.launch {
            val existing = uiState.value.budgets.find { it.categoryId == categoryId && it.periodMonth == "DEFAULT" }
            if (existing != null) {
                repository.insertBudget(existing.copy(limitAmount = limitAmount))
            } else {
                repository.insertBudget(
                    BudgetEntity(
                        categoryId = categoryId,
                        limitAmount = limitAmount,
                        periodMonth = "DEFAULT"
                    )
                )
            }
            _statusMessage.value = "Лимит бюджета установлен"
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
            _statusMessage.value = "Бюджет удален"
        }
    }

    fun addGoal(name: String, targetAmount: Double, currentAmount: Double = 0.0, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.insertGoal(
                GoalEntity(
                    name = name,
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
            _statusMessage.value = "Цель «$name» создана"
        }
    }

    fun contributeToGoal(goalId: Long, amount: Double) {
        viewModelScope.launch {
            repository.contributeToGoal(goalId, amount)
            _statusMessage.value = "В копилку добавлено $amount"
        }
    }

    fun updateGoal(goal: GoalEntity) {
        viewModelScope.launch {
            repository.updateGoal(goal)
            _statusMessage.value = "Копилка обновлена"
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            _statusMessage.value = "Цель удалена"
        }
    }

    fun updateDebt(debt: DebtEntity) {
        viewModelScope.launch {
            repository.updateDebt(debt)
            _statusMessage.value = "Долг обновлен"
        }
    }

    fun addDebt(personName: String, amount: Double, isOwedToMe: Boolean, note: String) {
        viewModelScope.launch {
            repository.insertDebt(
                DebtEntity(
                    personName = personName,
                    amount = amount,
                    isOwedToMe = isOwedToMe,
                    note = note,
                    isSettled = false
                )
            )
            _statusMessage.value = "Запись о долге сохранена"
        }
    }

    fun toggleDebtSettled(debt: DebtEntity) {
        viewModelScope.launch {
            repository.updateDebt(debt.copy(isSettled = !debt.isSettled))
            _statusMessage.value = if (!debt.isSettled) "Долг закрыт" else "Долг возобновлен"
        }
    }

    fun deleteDebt(debt: DebtEntity) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
            _statusMessage.value = "Долг удален"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    // Pending Notifications management
    fun confirmPendingNotification(
        notification: PendingNotificationEntity,
        accountId: Long,
        categoryId: Long?
    ) {
        viewModelScope.launch {
            val isZenmoney = notification.packageName == "ru.zenmoney.androidsub" || notification.packageName == "ru.zenmoney.android"
            var note = "${notification.bankName}: ${notification.merchantOrSender}"
            if (isZenmoney) {
                // We prepended the category to rawText in BankNotificationListener for Zenmoney
                val cat = notification.rawText.substringBefore(":")
                if (cat.isNotBlank() && cat != notification.rawText) {
                    note = cat.trim()
                }
            }
            
            repository.addTransaction(
                TransactionEntity(
                    type = notification.type,
                    amount = notification.amount,
                    accountId = accountId,
                    categoryId = categoryId,
                    timestamp = notification.timestamp,
                    note = note,
                    tag = if (isZenmoney) "дзен-мани" else "банк-авто"
                )
            )
            repository.markNotificationProcessed(notification.id)
            _statusMessage.value = "Операция подтверждена и добавлена"
        }
    }

    fun dismissPendingNotification(notification: PendingNotificationEntity) {
        viewModelScope.launch {
            repository.markNotificationProcessed(notification.id)
            _statusMessage.value = "Уведомление пропущено"
        }
    }

    fun deletePendingNotification(notification: PendingNotificationEntity) {
        viewModelScope.launch {
            repository.deletePendingNotification(notification)
        }
    }

    fun parseAndProcessManualText(text: String) {
        viewModelScope.launch {
            val parsed = com.example.service.BankNotificationParser.parse(text)
            if (parsed == null || parsed.amount <= 0) {
                _statusMessage.value = "Не удалось распознать банковское сообщение"
                return@launch
            }

            val accounts = repository.activeAccounts.firstOrNull() ?: emptyList()
            val matchedAccount = accounts.find { acc ->
                (parsed.cardLast4 != null && acc.name.contains(parsed.cardLast4)) ||
                        acc.name.contains(parsed.bankName, ignoreCase = true) ||
                        (acc.currency == parsed.currency && !acc.isArchived)
            } ?: accounts.firstOrNull()

            val categories = repository.allCategories.firstOrNull() ?: emptyList()
            val matchedCatId = com.example.service.BankNotificationParser.matchCategoryId(
                categories,
                parsed.matchedCategoryKeyword
            )

            val pending = PendingNotificationEntity(
                packageName = "manual.input",
                bankName = parsed.bankName,
                rawText = text,
                type = parsed.type,
                amount = parsed.amount,
                currency = parsed.currency,
                merchantOrSender = parsed.merchant,
                cardLast4 = parsed.cardLast4,
                suggestedAccountId = matchedAccount?.id,
                suggestedCategoryId = matchedCatId
            )
            repository.insertPendingNotification(pending)
            _statusMessage.value = "Сообщение распознано: ${parsed.amount} ${parsed.currency} (${parsed.merchant})"
        }
    }

    fun clearAllData(keepAccountStructure: Boolean = true) {
        viewModelScope.launch {
            repository.clearAllData(keepAccountStructure)
            _statusMessage.value = if (keepAccountStructure) {
                "Все операции очищены, балансы счетов обнулены"
            } else {
                "Все данные и счета полностью очищены"
            }
        }
    }

    fun resetToDemoData() {
        viewModelScope.launch {
            repository.resetData()
            _statusMessage.value = "Базовые данные восстановлены"
        }
    }

    // --- Gemini AI Assistant Actions ---

    fun saveGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        geminiPrefs.saveApiKey(trimmed)
        _userApiKey.value = trimmed
        _isAiConfigured.value = geminiPrefs.isApiKeyConfigured()
        _statusMessage.value = if (trimmed.isNotBlank()) "Ключ Gemini API сохранен" else "Ключ Gemini API удален"
    }

    fun clearGeminiApiKey() {
        geminiPrefs.clearApiKey()
        _userApiKey.value = ""
        _isAiConfigured.value = geminiPrefs.isApiKeyConfigured()
        _statusMessage.value = "Ключ Gemini API удален"
    }

    fun testGeminiApiKey(apiKey: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = geminiService.testApiKey(apiKey)
            if (result.isSuccess) {
                onResult(true, result.getOrNull() ?: "Ключ успешно проверен и работает!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Не удалось проверить ключ")
            }
        }
    }

    fun clearAiChat() {
        _aiMessages.value = emptyList()
        _aiState.value = AiState.Idle
    }

    fun updateAiInputText(text: String) {
        _aiInputText.value = text
    }

    fun setPayday(day: Int) {
        val validDay = day.coerceIn(1, 31)
        userFinancePrefs.setPaydayDay(validDay)
        _payday.value = validDay
        _statusMessage.value = "День зарплаты установлен на $validDay-е число"
    }

    fun setBankOfTheMonth(bank: String) {
        userFinancePrefs.setBankOfTheMonth(bank)
        _bankOfTheMonth.value = bank
        _statusMessage.value = if (bank == UserFinancePreferences.BANK_YANDEX) {
            "Яндекс Банк выбран основным банком месяца (кэшбэк)"
        } else {
            "ВТБ установлен основным банком"
        }
    }

    fun executeMe2MeTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        note: String = "Перевод между своими счетами"
    ) {
        if (fromAccountId == toAccountId) {
            _statusMessage.value = "Счета списания и зачисления должны различаться"
            return
        }
        if (amount <= 0) {
            _statusMessage.value = "Сумма перевода должна быть больше нуля"
            return
        }
        viewModelScope.launch {
            repository.addTransaction(
                TransactionEntity(
                    type = "TRANSFER",
                    amount = amount,
                    accountId = fromAccountId,
                    toAccountId = toAccountId,
                    timestamp = System.currentTimeMillis(),
                    note = note.ifBlank { "Перевод между своими счетами" },
                    tag = "me2me,перевод",
                    excludeFromStats = false
                )
            )
            _statusMessage.value = "Перевод успешно выполнен"
        }
    }

    fun executeIncomeDistribution(
        sourceAccountId: Long,
        allocations: List<com.example.ui.components.AllocationItemData>,
        notePrefix: String = "Распределение средств"
    ) {
        viewModelScope.launch {
            var count = 0
            val now = System.currentTimeMillis()
            allocations.filter { it.amount > 0 }.forEach { alloc ->
                when (alloc.type) {
                    com.example.ui.components.AllocationTargetType.ACCOUNT -> {
                        if (alloc.targetId != sourceAccountId) {
                            repository.addTransaction(
                                TransactionEntity(
                                    type = "TRANSFER",
                                    amount = alloc.amount,
                                    accountId = sourceAccountId,
                                    toAccountId = alloc.targetId,
                                    timestamp = now + (count * 200),
                                    note = "$notePrefix",
                                    tag = "распределение,шлюз,me2me",
                                    excludeFromStats = false
                                )
                            )
                            count++
                        }
                    }
                    com.example.ui.components.AllocationTargetType.CATEGORY -> {
                        repository.addTransaction(
                            TransactionEntity(
                                type = "EXPENSE",
                                amount = alloc.amount,
                                accountId = sourceAccountId,
                                categoryId = alloc.targetId,
                                timestamp = now + (count * 200),
                                note = "$notePrefix",
                                tag = "распределение,шлюз,категория",
                                excludeFromStats = false
                            )
                        )
                        count++
                    }
                    com.example.ui.components.AllocationTargetType.GOAL -> {
                        // Goals might just be tracked via expenses or transfers, but let's just make it a transfer to the first savings account or an expense.
                        // Actually, ZenMoney doesn't have a transaction type 'GOAL'. But wait, Mani-mani Goals are tracking progress.
                        // For now, let's just create an EXPENSE to a dummy savings category, or a special type. 
                        // Wait, "Goal" in ManiMani is just a GoalEntity. We might need to just update the Goal's currentAmount.
                        // But wait, where does the money go? It stays in the account unless transferred.
                        // If they distribute to a Goal, they might want to just increase the Goal progress and deduct from the source account.
                        // Let's create an EXPENSE transaction without a category, but with a note, and update the Goal progress.
                        repository.addTransaction(
                            TransactionEntity(
                                type = "EXPENSE",
                                amount = alloc.amount,
                                accountId = sourceAccountId,
                                timestamp = now + (count * 200),
                                note = "$notePrefix (Копилка/Цель)",
                                tag = "распределение,шлюз,цель",
                                excludeFromStats = false
                            )
                        )
                        // Also update goal
                        repository.contributeToGoal(alloc.targetId, alloc.amount)
                        count++
                    }
                    com.example.ui.components.AllocationTargetType.DEBT -> {
                        repository.addTransaction(
                            TransactionEntity(
                                type = "EXPENSE",
                                amount = alloc.amount,
                                accountId = sourceAccountId,
                                timestamp = now + (count * 200),
                                note = "$notePrefix (Погашение долга/кредита)",
                                tag = "распределение,шлюз,долг",
                                excludeFromStats = false
                            )
                        )
                        val debt = uiState.value.debts.find { it.id == alloc.targetId }
                        if (debt != null) {
                            val newAmount = (debt.amount - alloc.amount).coerceAtLeast(0.0)
                            repository.updateDebt(debt.copy(amount = newAmount, isSettled = newAmount == 0.0))
                        }
                        count++
                    }
                }
            }
            _statusMessage.value = "Успешно создано $count операций распределения"
        }
    }

    fun applyUserBankStructure() {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication(), viewModelScope)
            AppDatabase.applyUserBankStructure(db)
            _statusMessage.value = "Активирована структура ваших банков (ВТБ, Т-Банк, Озон, Альфа, Яндекс)"
        }
    }

    fun askGemini(promptType: AiPromptType, userQuestion: String? = null) {
        val currentState = uiState.value
        if (!geminiPrefs.isApiKeyConfigured()) {
            _aiState.value = AiState.Error("Ключ Gemini API не настроен. Перейдите в Настройки и введите ваш ключ.", isApiKeyMissing = true)
            return
        }

        val questionLabel = if (!userQuestion.isNullOrBlank()) {
            userQuestion
        } else {
            "${promptType.iconEmoji} ${promptType.title}"
        }

        val userMessage = AiMessage(
            sender = MessageSender.USER,
            text = questionLabel,
            promptType = promptType
        )

        _aiMessages.value = _aiMessages.value + userMessage
        _aiState.value = AiState.Loading

        viewModelScope.launch {
            val result = geminiService.askAssistant(
                promptType = promptType,
                userQuestion = userQuestion,
                accounts = currentState.accounts,
                categories = currentState.categories,
                transactions = currentState.transactions,
                budgets = currentState.budgetProgresses,
                goals = currentState.goals,
                debts = currentState.debts,
                baseCurrency = currentState.baseCurrency,
                monthlyIncome = currentState.monthlyIncome,
                monthlyExpense = currentState.monthlyExpense,
                categorySpendings = currentState.categorySpendings,
                conversationHistory = _aiMessages.value,
                payday = currentState.payday,
                paydayPeriodLabel = currentState.paydayPeriodLabel,
                daysUntilPayday = currentState.daysUntilPayday,
                bankOfTheMonth = currentState.bankOfTheMonth
            )

            if (result.isSuccess) {
                val answer = result.getOrNull() ?: ""
                val assistantMessage = AiMessage(
                    sender = MessageSender.ASSISTANT,
                    text = answer,
                    promptType = promptType
                )
                _aiMessages.value = _aiMessages.value + assistantMessage
                _aiState.value = AiState.Success(answer)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Произошла ошибка при обращении к Gemini"
                val isKeyMissing = errorMsg.contains("не настроен", ignoreCase = true)
                _aiState.value = AiState.Error(errorMsg, isApiKeyMissing = isKeyMissing)
            }
        }
    }
    // Planned Transactions
    fun addPlannedTransaction(transaction: PlannedTransactionEntity) {
        viewModelScope.launch {
            repository.insertPlannedTransaction(transaction)
        }
    }

    fun updatePlannedTransaction(transaction: PlannedTransactionEntity) {
        viewModelScope.launch {
            repository.updatePlannedTransaction(transaction)
        }
    }

    fun deletePlannedTransaction(transaction: PlannedTransactionEntity) {
        viewModelScope.launch {
            repository.deletePlannedTransaction(transaction)
        }
    }


    // --- Backup & Restore ---
    fun exportBackupLocal(uri: Uri) {
        viewModelScope.launch {
            try {
                backupRepository.exportLocal(uri)
                _statusMessage.value = "Локальный бэкап успешно сохранен"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка при экспорте: ${e.message}"
            }
        }
    }

    fun importBackupLocal(uri: Uri) {
        viewModelScope.launch {
            try {
                backupRepository.importLocal(uri)
                _statusMessage.value = "Данные успешно восстановлены из файла"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка при импорте: ${e.message}"
            }
        }
    }

    fun exportBackupCloud(token: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Сохраняем данные в Яндекс.Диск..."
                backupRepository.exportCloud(token)
                _statusMessage.value = "Бэкап успешно сохранен в Яндекс.Диск"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка облачного экспорта: ${e.message}"
            }
        }
    }

    fun importBackupCloud(token: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Загружаем данные из Яндекс.Диска..."
                backupRepository.importCloud(token)
                _statusMessage.value = "Данные из облака успешно восстановлены"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка облачного импорта: ${e.message}"
            }
        }
    }

    fun completeFirstLaunch() {
        userFinancePrefs.setFirstLaunchCompleted()
        _isFirstLaunch.value = false
    }
}