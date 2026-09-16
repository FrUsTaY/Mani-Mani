package com.example.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.gemini.AiPromptType
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper
import com.example.ui.viewmodel.CategorySpending
import com.example.ui.viewmodel.FinanceUiState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

enum class AnalyticsPeriodType(val title: String) {
    PAYDAY("По циклу зарплаты"),
    CURRENT_MONTH("Текущий месяц"),
    PREV_MONTH("Прошлый месяц"),
    LAST_30_DAYS("Последние 30 дней"),
    ALL_TIME("Все время")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    state: FinanceUiState,
    onOpenGeminiAssistant: (AiPromptType) -> Unit = {},
    onAskAiQuestion: (String) -> Unit = {},
    onNavigateToPlanning: () -> Unit = {},
    onOpenPaydaySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedPeriodType by remember { mutableStateOf(AnalyticsPeriodType.PAYDAY) }
    var showPeriodDropdown by remember { mutableStateOf(false) }

    // Bottom sheet states
    var showIncomeExpenseSheet by remember { mutableStateOf(false) }
    var showCategoryExpenseSheet by remember { mutableStateOf(false) }
    var showPeriodComparisonSheet by remember { mutableStateOf(false) }
    var showSavingsDynamicsSheet by remember { mutableStateOf(false) }
    var showFreeMoneySheet by remember { mutableStateOf(false) }

    val accountsMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val categoriesMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val goalsMap = remember(state.goals) { state.goals.associateBy { it.id } }
    val debtsMap = remember(state.debts) { state.debts.associateBy { it.id } }

    // Today label formatted like in Zen-money: "13 сен"
    val todayLabel = remember {
        SimpleDateFormat("d MMM", Locale("ru")).format(Date())
    }

    // Determine period label & boundaries
    val periodLabel = remember(selectedPeriodType, state.paydayPeriodLabel) {
        when (selectedPeriodType) {
            AnalyticsPeriodType.PAYDAY -> state.paydayPeriodLabel.ifEmpty { "10 сен — 9 окт" }
            AnalyticsPeriodType.CURRENT_MONTH -> {
                val cal = Calendar.getInstance()
                SimpleDateFormat("LLLL yyyy", Locale("ru")).format(cal.time).replaceFirstChar { it.uppercase() }
            }
            AnalyticsPeriodType.PREV_MONTH -> {
                val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                SimpleDateFormat("LLLL yyyy", Locale("ru")).format(cal.time).replaceFirstChar { it.uppercase() }
            }
            AnalyticsPeriodType.LAST_30_DAYS -> "Последние 30 дней"
            AnalyticsPeriodType.ALL_TIME -> "Все время"
        }
    }

    // Filter transactions based on selected period
    val now = System.currentTimeMillis()
    val (filteredIncome, filteredExpense, filteredSpendings, filteredTxs) = remember(
        selectedPeriodType,
        state.transactions,
        state.accounts,
        state.categories,
        state.baseCurrency
    ) {
        val (startTime, endTime) = when (selectedPeriodType) {
            AnalyticsPeriodType.PAYDAY -> {
                // Approximate 30 day period or actual payday cycle
                val oneDay = 86_400_000L
                val cycleStart = now - (state.dayOfCycle - 1) * oneDay
                val cycleEnd = cycleStart + (state.totalDaysInCycle * oneDay)
                Pair(cycleStart, cycleEnd)
            }
            AnalyticsPeriodType.CURRENT_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val start = cal.timeInMillis
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                Pair(start, cal.timeInMillis)
            }
            AnalyticsPeriodType.PREV_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val start = cal.timeInMillis
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                Pair(start, cal.timeInMillis)
            }
            AnalyticsPeriodType.LAST_30_DAYS -> {
                Pair(now - 30L * 86_400_000L, now)
            }
            AnalyticsPeriodType.ALL_TIME -> {
                Pair(0L, Long.MAX_VALUE)
            }
        }

        val txs = state.transactions.filter {
            it.timestamp in startTime..endTime && !it.excludeFromStats
        }

        val inc = txs.sumOf { tx ->
            val acc = state.accounts.find { it.id == tx.accountId }
            val toAcc = state.accounts.find { it.id == tx.toAccountId }
            val accInAnalytics = acc?.includeInAnalytics ?: true
            val toAccInAnalytics = toAcc?.includeInAnalytics ?: true

            val convertedAmount = CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)

            if (tx.type == "INCOME" && accInAnalytics) {
                convertedAmount
            } else if (tx.type == "TRANSFER" && !accInAnalytics && toAccInAnalytics) {
                convertedAmount
            } else {
                0.0
            }
        }
        val exp = txs.sumOf { tx ->
            val acc = state.accounts.find { it.id == tx.accountId }
            val toAcc = state.accounts.find { it.id == tx.toAccountId }
            val accInAnalytics = acc?.includeInAnalytics ?: true
            val toAccInAnalytics = toAcc?.includeInAnalytics ?: true
            
            val convertedAmount = CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)

            if (tx.type == "EXPENSE" && accInAnalytics) {
                convertedAmount
            } else if (tx.type == "TRANSFER" && accInAnalytics && !toAccInAnalytics) {
                convertedAmount
            } else {
                0.0
            }
        }

        val catMap = state.categories.associateBy { it.id }
        val spendMap = mutableMapOf<Long, Double>()
        txs.forEach { tx ->
            val acc = state.accounts.find { it.id == tx.accountId }
            val toAcc = state.accounts.find { it.id == tx.toAccountId }
            val accInAnalytics = acc?.includeInAnalytics ?: true
            val toAccInAnalytics = toAcc?.includeInAnalytics ?: true
            val convertedAmount = CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)
            
            val isExpense = tx.type == "EXPENSE" && accInAnalytics
            val isTransferOut = tx.type == "TRANSFER" && accInAnalytics && !toAccInAnalytics

            if (isExpense || isTransferOut) {
                val catId = tx.categoryId ?: -1L
                spendMap[catId] = (spendMap[catId] ?: 0.0) + convertedAmount
            }
        }

        val spendings = spendMap.mapNotNull { (catId, amount) ->
            val cat = catMap[catId] ?: com.example.data.entity.CategoryEntity(id = -1L, name = "Переводы", iconName = "swap_horiz", colorHex = "#9E9E9E", type = "EXPENSE")
            val pct = if (exp > 0) (amount / exp).toFloat() else 0f
            CategorySpending(cat, amount, pct)
        }.sortedByDescending { it.totalAmount }

        Quad(inc, exp, spendings, txs)
    }

    // Previous period expense for comparison
    val prevPeriodExpense = remember(state.transactions, state.accounts, state.baseCurrency) {
        val thirtyDaysMs = 30L * 86_400_000L
        val prevStart = now - (2 * thirtyDaysMs)
        val prevEnd = now - thirtyDaysMs
        state.transactions.filter {
            it.timestamp in prevStart..prevEnd && !it.excludeFromStats
        }.sumOf { tx ->
            val acc = state.accounts.find { it.id == tx.accountId }
            val toAcc = state.accounts.find { it.id == tx.toAccountId }
            val accInAnalytics = acc?.includeInAnalytics ?: true
            val toAccInAnalytics = toAcc?.includeInAnalytics ?: true
            
            val convertedAmount = CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)

            if (tx.type == "EXPENSE" && accInAnalytics) {
                convertedAmount
            } else if (tx.type == "TRANSFER" && accInAnalytics && !toAccInAnalytics) {
                convertedAmount
            } else {
                0.0
            }
        }
    }

    // Planning metrics calculation
    val totalBudgetLimits = state.budgets.sumOf { it.limitAmount }
    val remainingPlanned = if (totalBudgetLimits > 0) {
        (totalBudgetLimits - filteredExpense).coerceAtLeast(0.0)
    } else {
        0.0
    }

    val forecastCeiling = (filteredExpense + remainingPlanned * 1.28).coerceAtLeast(filteredExpense * 1.2)
    val freeMoney = (state.totalBalance - remainingPlanned).coerceAtLeast(0.0)
    val savingsInGoals = state.goals.sumOf { it.currentAmount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            // 1. Top Bar: "Аналитика" + Gemini Assistant + Settings
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Аналитика",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // AI Assistant button
                        IconButton(
                            onClick = { onOpenGeminiAssistant(AiPromptType.FULL_AUDIT) },
                            modifier = Modifier.testTag("analytics_ai_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Финансовый ИИ-аудит",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Settings Gear
                        IconButton(
                            onClick = onOpenPaydaySettings,
                            modifier = Modifier.testTag("analytics_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки аналитики и периода",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 2. Period Selector (10 сен — 9 окт ▾)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showPeriodDropdown = true }
                                .testTag("analytics_period_selector")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = periodLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Выбрать период",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPeriodDropdown,
                            onDismissRequest = { showPeriodDropdown = false }
                        ) {
                            AnalyticsPeriodType.values().forEach { periodType ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = when (periodType) {
                                                AnalyticsPeriodType.PAYDAY -> "По зарплате (${state.paydayPeriodLabel.ifEmpty { "цикл" }})"
                                                AnalyticsPeriodType.CURRENT_MONTH -> "Текущий календарный месяц"
                                                AnalyticsPeriodType.PREV_MONTH -> "Прошлый месяц"
                                                AnalyticsPeriodType.LAST_30_DAYS -> "Последние 30 дней"
                                                AnalyticsPeriodType.ALL_TIME -> "Все время"
                                            },
                                            fontWeight = if (selectedPeriodType == periodType) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedPeriodType = periodType
                                        showPeriodDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Card 1: "Доходы vs Расходы" -> opens detailed BottomSheet
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ZenIncomeVsExpenseCard(
                        income = filteredIncome,
                        expense = filteredExpense,
                        periodLabel = periodLabel,
                        currency = state.baseCurrency,
                        onOpenDetails = { showIncomeExpenseSheet = true }
                    )
                }
            }

            // 3. Card 2: "Анализ расходов по категориям" -> opens detailed BottomSheet
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ZenCategoryAnalysisCard(
                        spendings = if (filteredSpendings.isNotEmpty()) filteredSpendings else state.categorySpendings,
                        totalExpense = filteredExpense,
                        currency = state.baseCurrency,
                        onOpenDetails = { showCategoryExpenseSheet = true }
                    )
                }
            }

            // 4. Card 3: "Сравнение периодов"
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ZenPeriodComparisonCard(
                        currentExpense = filteredExpense,
                        prevExpense = if (prevPeriodExpense > 0) prevPeriodExpense else (filteredExpense * 0.88),
                        currency = state.baseCurrency,
                        onClick = { showPeriodComparisonSheet = true }
                    )
                }
            }

            // 5. Card 4: "Динамика накоплений"
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ZenSavingsDynamicsCard(
                        totalBalance = state.totalBalance,
                        savingsAmount = savingsInGoals,
                        currency = state.baseCurrency,
                        onClick = { showSavingsDynamicsSheet = true }
                    )
                }
            }

            // 6. Card 5: "Свободные деньги"
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ZenFreeMoneyCard(
                        freeMoney = freeMoney,
                        daysUntilPayday = state.daysUntilPayday,
                        currency = state.baseCurrency,
                        onClick = { showFreeMoneySheet = true }
                    )
                }
            }
        }

    // Detail Bottom Sheet: Доходы vs Расходы
    if (showIncomeExpenseSheet) {
        IncomeVsExpenseDetailSheet(
            income = filteredIncome,
            expense = filteredExpense,
            periodLabel = periodLabel,
            currency = state.baseCurrency,
            transactions = filteredTxs,
            accountsMap = accountsMap,
            categoriesMap = categoriesMap,
            goalsMap = goalsMap,
            debtsMap = debtsMap,
            onDismiss = { showIncomeExpenseSheet = false },
            onOpenGeminiAssistant = onOpenGeminiAssistant
        )
    }

    // Detail Bottom Sheet: Анализ расходов по категориям
    if (showCategoryExpenseSheet) {
        val expenseTransactions = remember(filteredTxs) {
            filteredTxs.filter { it.type == "EXPENSE" }
        }
        CategoryExpensesDetailSheet(
            spendings = if (filteredSpendings.isNotEmpty()) filteredSpendings else state.categorySpendings,
            totalExpense = filteredExpense,
            periodLabel = periodLabel,
            currency = state.baseCurrency,
            expenseTransactions = expenseTransactions,
            accountsMap = accountsMap,
            categoriesMap = categoriesMap,
            goalsMap = goalsMap,
            debtsMap = debtsMap,
            onDismiss = { showCategoryExpenseSheet = false }
        )
    }

    // Detail Bottom Sheet: Сравнение периодов
    if (showPeriodComparisonSheet) {
        val expenseTransactions = remember(filteredTxs) {
            filteredTxs.filter { it.type == "EXPENSE" }
        }
        PeriodComparisonDetailSheet(
            currentExpense = filteredExpense,
            prevExpense = if (prevPeriodExpense > 0) prevPeriodExpense else (filteredExpense * 0.88),
            periodLabel = periodLabel,
            currency = state.baseCurrency,
            currentTransactions = expenseTransactions,
            accountsMap = accountsMap,
            categoriesMap = categoriesMap,
            goalsMap = goalsMap,
            debtsMap = debtsMap,
            onDismiss = { showPeriodComparisonSheet = false }
        )
    }

    // Detail Bottom Sheet: Динамика накоплений
    if (showSavingsDynamicsSheet) {
        SavingsDynamicsDetailSheet(
            totalBalance = state.totalBalance,
            savingsAmount = savingsInGoals,
            goals = state.goals,
            currency = state.baseCurrency,
            onDismiss = { showSavingsDynamicsSheet = false }
        )
    }

    // Detail Bottom Sheet: Свободные деньги
    if (showFreeMoneySheet) {
        FreeMoneyDetailSheet(
            freeMoney = freeMoney,
            daysUntilPayday = state.daysUntilPayday,
            currency = state.baseCurrency,
            totalBalance = state.totalBalance,
            remainingPlannedExpenses = remainingPlanned,
            onDismiss = { showFreeMoneySheet = false }
        )
    }
}
