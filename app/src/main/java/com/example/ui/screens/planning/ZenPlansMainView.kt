package com.example.ui.screens.planning

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.PlannedTransactionEntity
import com.example.service.UserFinancePreferences
import com.example.service.gemini.AiPromptType
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.viewmodel.FinanceUiState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min



data class PlannedPaymentItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val dayOfMonth: Int = 10,
    val categoryName: String = ""
)

/**
 * The main "Планы" view matching Zen-money's full UI/UX from the screenshots.
 */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ZenPlansMainView(
    state: FinanceUiState,
    onAddBudget: (categoryId: Long?, limitAmount: Double) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    onAddPlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onUpdatePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onAddTransaction: (com.example.data.entity.TransactionEntity) -> Unit = {},
    selectedSubTab: Int = 0,
    onSubTabSelected: (Int) -> Unit = {},
    subTabs: List<String> = listOf("Планы", "Копилки", "Долги"),
    onOpenPaydaySettings: () -> Unit = {},
    onOpenGeminiAssistant: (AiPromptType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember { UserFinancePreferences(context) }

    // Cycle offset: 0 = current cycle, -1 = previous, +1 = next
    var cycleOffset by remember { mutableIntStateOf(0) }

    // Calculate active payday period based on cycle offset
    val period = remember(state.payday, cycleOffset) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, cycleOffset)
        }
        preferences.calculatePaydayPeriod(state.payday, cal.timeInMillis)
    }

    val todayDateLabel = remember {
        SimpleDateFormat("d MMM", Locale("ru")).format(Date())
    }

    val endPeriodDateLabel = remember(period.endTime) {
        val cal = Calendar.getInstance().apply { timeInMillis = period.endTime }
        SimpleDateFormat("d MMM", Locale("ru")).format(cal.time)
    }


    // Modal dialog states
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPlannedPaymentsSheet by remember { mutableStateOf(false) }
    var showAddPlannedPaymentDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }
    var itemToExecute by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }
    var categoryToEditPlan by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddCategoryPlanDialog by remember { mutableStateOf(false) }

    // Collapsible states
    var isMoneyExpanded by remember { mutableStateOf(false) }
    var isExpensesExpanded by remember { mutableStateOf(true) }

    // Calculate current cycle finances
    val now = System.currentTimeMillis()
    val isCurrentCycle = cycleOffset == 0

    // Filter transactions in this cycle for analytical spending / category budgets
    val cycleTransactions = remember(state.transactions, period.startTime, period.endTime) {
        state.transactions.filter {
            it.timestamp in period.startTime..period.endTime && !it.excludeFromStats
        }
    }

    val spentSoFar = remember(cycleTransactions, state.accounts, state.baseCurrency) {
        cycleTransactions.filter { 
            it.type == "EXPENSE" || (it.type == "TRANSFER" && it.goalId != null) 
        }.sumOf { tx ->
            val acc = state.accounts.find { it.id == tx.accountId }
            val inAnalytics = acc?.includeInAnalytics ?: true
            if (inAnalytics) {
                CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)
            } else {
                0.0
            }
        }
    }

    val incomeSoFar = remember(cycleTransactions, state.accounts, state.baseCurrency) {
        cycleTransactions.filter { it.type == "INCOME" }.sumOf { tx ->
            val acc = state.accounts.find { it.id == tx.accountId }
            val inAnalytics = acc?.includeInAnalytics ?: true
            if (inAnalytics) {
                CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)
            } else {
                0.0
            }
        }
    }

    // Spending per category in this cycle
    val spendingByCategory = remember(cycleTransactions, state.accounts, state.baseCurrency) {
        val map = mutableMapOf<Long, Double>()
        cycleTransactions.filter { 
            it.type == "EXPENSE" || (it.type == "TRANSFER" && it.goalId != null) 
        }.forEach { tx ->
            val catId = when {
                tx.goalId != null -> -10000L - tx.goalId
                tx.debtId != null -> -20000L - tx.debtId
                else -> tx.categoryId ?: -1L
            }
            val acc = state.accounts.find { it.id == tx.accountId }
            val inAnalytics = acc?.includeInAnalytics ?: true
            if (inAnalytics) {
                val amount = CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)
                map[catId] = (map[catId] ?: 0.0) + amount
            }
        }
        map
    }

    // Budgets mapped by categoryId
    val budgetMap = remember(state.budgets) {
        state.budgets.associateBy { it.categoryId }
    }

    // Sum of planned category budgets
    val totalCategoryBudgets = state.budgets.sumOf { it.limitAmount }

    // Planned upcoming payments in this cycle
    val remainingPlannedPayments = state.plannedTransactions
        .filter { it.type == "EXPENSE" && (Calendar.getInstance().apply { timeInMillis = it.plannedDate }.get(Calendar.DAY_OF_MONTH) >= period.dayOfCycle || !isCurrentCycle) }
        .sumOf { it.amount }

    val remainingPlannedIncome = state.plannedTransactions
        .filter { it.type == "INCOME" && (Calendar.getInstance().apply { timeInMillis = it.plannedDate }.get(Calendar.DAY_OF_MONTH) >= period.dayOfCycle || !isCurrentCycle) }
        .sumOf { it.amount }

    // "Ещё в планах" (Remaining planned expenses)
    val remainingCategoryBudgets = state.budgets.sumOf { budget ->
        val spentInCat = spendingByCategory[budget.categoryId] ?: 0.0
        (budget.limitAmount - spentInCat).coerceAtLeast(0.0)
    }
    val remainingPlannedExpenses = remainingCategoryBudgets + remainingPlannedPayments

    // Calculate the actual net balance change of all included accounts during this cycle
    // to accurately reconstruct the baseline starting balance on the cycle start date.
    val netTotalBalanceChangeInCycle = remember(state.transactions, state.accounts, state.baseCurrency, period.startTime, period.endTime) {
        val cycleAllTx = state.transactions.filter { it.timestamp in period.startTime..period.endTime }
        var change = 0.0
        for (tx in cycleAllTx) {
            val acc = state.accounts.find { it.id == tx.accountId }
            val toAcc = state.accounts.find { it.id == tx.toAccountId }
            val accInTotal = (acc?.includeInTotal == true && !acc.isArchived)
            val toAccInTotal = (toAcc?.includeInTotal == true && !toAcc.isArchived)
            val amount = CurrencyHelper.convert(tx.amount, acc?.currency ?: state.baseCurrency, state.baseCurrency)

            when (tx.type) {
                "EXPENSE" -> {
                    if (accInTotal) {
                        change -= amount
                    }
                }
                "INCOME" -> {
                    if (accInTotal) {
                        change += amount
                    }
                }
                "TRANSFER" -> {
                    if (accInTotal && !toAccInTotal) {
                        change -= amount
                    } else if (!accInTotal && toAccInTotal) {
                        val toAmount = CurrencyHelper.convert(tx.amount, toAcc?.currency ?: state.baseCurrency, state.baseCurrency)
                        change += toAmount
                    }
                }
            }
        }
        change
    }

    // "Деньги на месяц" (Total funds available this cycle)
    // True baseline at start of period = current totalBalance - (netChangeInCycle)
    val baselineStartingBalance = (state.totalBalance - netTotalBalanceChangeInCycle).coerceAtLeast(0.0)
    val totalMoneyForMonth = baselineStartingBalance + incomeSoFar + remainingPlannedIncome

    // "Свободно на конец месяца" (Free money at end of month)
    val totalProjectedExpenses = spentSoFar + remainingPlannedExpenses
    // Free money is current balance + future income - future remaining expenses
    val freeMoneyAtEndOfMonth = (state.totalBalance + remainingPlannedIncome - remainingPlannedExpenses).coerceAtLeast(0.0)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Bar: "Планы" + Calendar Counter [📅 0] + Info + Settings
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Планы",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Calendar Badge [ 📅 count ]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .clickable { showPlannedPaymentsSheet = true }
                                .testTag("zen_plans_calendar_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = "Запланированные операции",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${state.plannedTransactions.size}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Settings Gear
                        IconButton(
                            onClick = onOpenPaydaySettings,
                            modifier = Modifier.testTag("zen_plans_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки планов и дня зарплаты",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Sub-tabs segment switcher: [ Планы | Копилки | Долги ]
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subTabs.forEachIndexed { index, title ->
                        val isSelected = selectedSubTab == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSubTabSelected(index) }
                                .testTag("planning_subtab_$index")
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Subheader Row: [ 🟢⚫ Расходы ] and < 10 сен – 9 окт >
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Pill: [ 🟢⚫ Расходы ]
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Green dot
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34A853))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Dot
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Бюджеты по категориям",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Right Cycle Navigator: < 10 сен – 9 окт >
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("zen_plans_cycle_navigator")
                    ) {
                        IconButton(
                            onClick = { cycleOffset -= 1 },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Предыдущий цикл",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = period.periodLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = { cycleOffset += 1 },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Следующий цикл",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Top Metrics: "С начала месяца" & "Ещё в планах"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "С начала месяца",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyHelper.formatAmount(spentSoFar, state.baseCurrency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        Text(
                            text = "Ещё в планах",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyHelper.formatAmount(remainingPlannedExpenses, state.baseCurrency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF1E88E5),
                            fontSize = 28.sp
                        )
                    }
                }
            }

            // 4. Interactive Zen Forecast Line Chart
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    ZenDetailedForecastChart(
                        startingMoney = baselineStartingBalance,
                        totalMoney = totalMoneyForMonth,
                        spentSoFar = spentSoFar,
                        projectedTotalExpenses = totalProjectedExpenses,
                        dayOfCycle = if (isCurrentCycle) period.dayOfCycle else if (cycleOffset < 0) period.totalDaysInCycle else 1,
                        totalDaysInCycle = period.totalDaysInCycle,
                        todayDateLabel = todayDateLabel,
                        endDateLabel = endPeriodDateLabel,
                        currency = state.baseCurrency
                    )
                }
            }

            // 5. "Свободно на конец месяца" Banner
            item {
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDarkTheme) Color(0xFF132E22) else Color(0xFFF0FDF4),
                        border = BorderStroke(
                            1.dp,
                            if (isDarkTheme) Color(0xFF22543D) else Color(0xFF81C784).copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("zen_plans_free_money_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ThumbUp,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF2E7D32),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Свободно на конец месяца",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = CurrencyHelper.formatAmount(freeMoneyAtEndOfMonth, state.baseCurrency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF2E7D32),
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // 6. Collapsible "Деньги на месяц" (with green sum)
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isMoneyExpanded = !isMoneyExpanded }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Ожидаемые операции",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isMoneyExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = CurrencyHelper.formatAmount(totalMoneyForMonth, state.baseCurrency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF4ADE80) else Color(0xFF2E7D32),
                                    fontSize = 17.sp
                                )
                            }

                            AnimatedVisibility(visible = isMoneyExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    MoneySubRow(
                                        title = "Баланс на начало периода",
                                        amount = baselineStartingBalance,
                                        currency = state.baseCurrency
                                    )
                                    MoneySubRow(
                                        title = "Фактические поступления",
                                        amount = incomeSoFar,
                                        currency = state.baseCurrency
                                    )
                                    MoneySubRow(
                                        title = "Будущие доходы (ожидаемые)",
                                        amount = remainingPlannedIncome,
                                        currency = state.baseCurrency
                                    )
                                    MoneySubRow(
                                        title = "Будущие расходы (календарные)",
                                        amount = remainingPlannedPayments,
                                        currency = state.baseCurrency
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { showAddPlannedPaymentDialog = true },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Добавить план / операцию")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Collapsible "Расходы" (with Category Rows & Dual Bars)
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpensesExpanded = !isExpensesExpanded }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Бюджеты по категориям",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isExpensesExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = CurrencyHelper.formatAmount(spentSoFar, state.baseCurrency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }

                            AnimatedVisibility(visible = isExpensesExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Expense categories list
                                    val expenseCategories = state.categories.filter { it.type == "EXPENSE" }.toMutableList()
                                    
                                    // Add Goals as pseudo-categories
                                    state.goals.forEach { goal ->
                                        expenseCategories.add(
                                            CategoryEntity(
                                                id = -10000L - goal.id,
                                                name = "Копилка: ${goal.name}",
                                                type = "EXPENSE",
                                                iconName = goal.iconName,
                                                colorHex = goal.colorHex
                                            )
                                        )
                                    }
                                    
                                    // Add Debts as pseudo-categories
                                    state.debts.forEach { debt ->
                                        expenseCategories.add(
                                            CategoryEntity(
                                                id = -20000L - debt.id,
                                                name = "Долг: ${debt.personName}",
                                                type = "EXPENSE",
                                                iconName = "account_balance",
                                                colorHex = "#F44336" // Default red or neutral
                                            )
                                        )
                                    }

                                    if (expenseCategories.isEmpty()) {
                                        Text(
                                            text = "Категории расходов пока не настроены",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                    } else {
                                        expenseCategories.forEach { category ->
                                            val catSpent = spendingByCategory[category.id] ?: 0.0
                                            val catBudget = budgetMap[category.id]
                                            val forecastAmount = catBudget?.limitAmount ?: catSpent

                                            ZenCategoryPlanRow(
                                                category = category,
                                                spentAmount = catSpent,
                                                forecastAmount = forecastAmount,
                                                hasCustomBudget = catBudget != null,
                                                currency = state.baseCurrency,
                                                onClick = {
                                                    categoryToEditPlan = category
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button (Edit) removed to avoid redundant UX
    }

    // Modal: "Планы в Дзене" (matching Screenshot 1)
    if (showInfoDialog) {
        ZenPlansInfoModal(
            onDismiss = { showInfoDialog = false },
            onOpenAiAudit = {
                showInfoDialog = false
                onOpenGeminiAssistant(AiPromptType.BUDGETS_AND_GOALS)
            }
        )
    }

    // Sheet: Planned Operations [📅 count]
    if (showPlannedPaymentsSheet) {
        ZenPlannedPaymentsDialog(
            items = state.plannedTransactions,
            currency = state.baseCurrency,
            onDismiss = { showPlannedPaymentsSheet = false },
            onAddItem = {
                itemToEdit = null
                showPlannedPaymentsSheet = false
                showAddPlannedPaymentDialog = true
            },
            onEditItem = { item ->
                itemToEdit = item
                showPlannedPaymentsSheet = false
                showAddPlannedPaymentDialog = true
            },
            onDeleteItem = { item ->
                onDeletePlannedTransaction(item)
            },
            onExecuteItem = { item ->
                itemToExecute = item
                showPlannedPaymentsSheet = false
            }
        )
    }

    // Dialog: Execute Planned Payment
    if (itemToExecute != null) {
        ExecutePlanDialog(
            item = itemToExecute!!,
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = { itemToExecute = null },
            onConfirm = { accountId, categoryId ->
                onAddTransaction(
                    com.example.data.entity.TransactionEntity(
                        type = itemToExecute!!.type,
                        amount = itemToExecute!!.amount,
                        accountId = accountId,
                        categoryId = categoryId,
                        note = itemToExecute!!.note,
                        timestamp = System.currentTimeMillis()
                    )
                )
                onDeletePlannedTransaction(itemToExecute!!)
                itemToExecute = null
            }
        )
    }

    // Dialog: Add Planned Payment
    if (showAddPlannedPaymentDialog) {
        AddPlannedPaymentDialog(
            existingItem = itemToEdit,
            onDismiss = { showAddPlannedPaymentDialog = false; itemToEdit = null },
            onConfirm = { title, amount, isIncome, day, reminderType ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                val newTimestamp = cal.timeInMillis
                if (itemToEdit != null) {
                    onUpdatePlannedTransaction(itemToEdit!!.copy(
                        note = title,
                        amount = amount,
                        type = if (isIncome) "INCOME" else "EXPENSE",
                        plannedDate = newTimestamp,
                        reminderType = reminderType
                    ))
                } else {
                    onAddPlannedTransaction(
                        com.example.data.entity.PlannedTransactionEntity(
                            type = if (isIncome) "INCOME" else "EXPENSE",
                            amount = amount,
                            accountId = state.accounts.firstOrNull()?.id ?: 1L,
                            plannedDate = newTimestamp,
                            note = title,
                            reminderType = reminderType
                        )
                    )
                }
                showAddPlannedPaymentDialog = false
                itemToEdit = null
            }
        )
    }

    // Dialog: Edit Category Plan / Forecast
    if (categoryToEditPlan != null) {
        val cat = categoryToEditPlan!!
        val currentBudget = budgetMap[cat.id]
        EditCategoryPlanDialog(
            category = cat,
            currentLimit = currentBudget?.limitAmount,
            currency = state.baseCurrency,
            onDismiss = { categoryToEditPlan = null },
            onSave = { newLimit ->
                onAddBudget(cat.id, newLimit)
                categoryToEditPlan = null
            },
            onDelete = {
                currentBudget?.let { onDeleteBudget(it) }
                categoryToEditPlan = null
            }
        )
    }

    // Dialog: Quick Add Category Plan from FAB
    if (showAddCategoryPlanDialog) {
        QuickAddPlanDialog(
            categories = state.categories.filter { it.type == "EXPENSE" },
            currency = state.baseCurrency,
            onDismiss = { showAddCategoryPlanDialog = false },
            onSelectCategory = { cat ->
                showAddCategoryPlanDialog = false
                categoryToEditPlan = cat
            },
            onAddPlannedPayment = {
                showAddCategoryPlanDialog = false
                showAddPlannedPaymentDialog = true
            }
        )
    }
}


/**
 * Detailed Zen Forecast Line Chart matching Screenshot 2 (INTERACTIVE)
 */
@Composable
fun ZenDetailedForecastChart(
    startingMoney: Double,
    totalMoney: Double,
    spentSoFar: Double,
    projectedTotalExpenses: Double,
    dayOfCycle: Int,
    totalDaysInCycle: Int,
    todayDateLabel: String,
    endDateLabel: String,
    currency: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val charcoalColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF2C3437)
    val forecastGreen = if (isDark) Color(0xFF4ADE80) else Color(0xFF34A853)
    val planBlue = if (isDark) Color(0xFF60A5FA) else Color(0xFF1E88E5)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val verticalGuideColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    // Calculate dynamic Y-axis maximum
    val ceilingVal = kotlin.math.max(totalMoney, kotlin.math.max(projectedTotalExpenses, spentSoFar)).coerceAtLeast(50000.0)
    val topTick = (Math.ceil(ceilingVal / 50000.0) * 50000.0).toInt()
    val midTick = topTick / 2

    var touchX by remember { mutableStateOf<Float?>(null) }
    var tooltipMoney by remember { mutableStateOf(0.0) }
    var tooltipSpend by remember { mutableStateOf(0.0) }
    var tooltipIsFuture by remember { mutableStateOf(false) }
    
    var chartWidthPx by remember { mutableStateOf(1f) }
    var leftPadPx by remember { mutableStateOf(0f) }
    
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("zen_detailed_forecast_chart")
    ) {
        // Date labels above chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 58.dp, end = 12.dp)
        ) {
            val cycleRatio = (dayOfCycle.toFloat() / kotlin.math.max(totalDaysInCycle, 1)).coerceIn(0.08f, 0.92f)

            // Today date label
            Text(
                text = todayDateLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (cycleRatio * 200).dp)
            )

            // End date label
            Text(
                text = endDateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Chart Canvas with Left Y-Axis Ticks
        
        val primaryColor = MaterialTheme.colorScheme.primary
        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> touchX = offset.x },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null },
                            onDrag = { change, _ ->
                                touchX = change.position.x
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height

                val leftPadding = 56.dp.toPx()
                val rightPadding = 16.dp.toPx()
                val topPadding = 24.dp.toPx() // Increased top padding for tooltips
                val bottomPadding = 16.dp.toPx()

                leftPadPx = leftPadding
                chartWidthPx = width - leftPadding - rightPadding
                val chartHeightPx = height - topPadding - bottomPadding

                // Horizontal Guidelines
                for (i in 0..2) {
                    val y = topPadding + (chartHeightPx / 2) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPadding, y),
                        end = Offset(width - rightPadding, y),
                        strokeWidth = 1f
                    )
                }

                val cycleRatio = (dayOfCycle.toFloat() / kotlin.math.max(totalDaysInCycle, 1)).coerceIn(0.0f, 1.0f)
                val currentX = leftPadding + chartWidthPx * cycleRatio

                // Normalized Y helpers
                fun toY(amount: Double): Float {
                    val norm = (amount / topTick).coerceIn(0.0, 1.0).toFloat()
                    return height - bottomPadding - (chartHeightPx * norm)
                }
                
                fun toValue(y: Float): Double {
                    val norm = (height - bottomPadding - y) / chartHeightPx
                    return (norm * topTick).toDouble().coerceAtLeast(0.0)
                }

                val moneyStartY = toY(startingMoney)
                val moneyCurrentY = toY(totalMoney * 0.72) // Visual approximation
                val moneyEndY = toY(totalMoney)

                val spendStartY = height - bottomPadding
                val spendCurrentY = toY(spentSoFar)
                val spendProjectedEndY = toY(projectedTotalExpenses)

                // 1. Solid Green Money Curve (Start -> Today)
                val moneySolidPath = Path().apply {
                    moveTo(leftPadding, moneyStartY)
                    val midX = (leftPadding + currentX) / 2
                    cubicTo(midX, moneyStartY, midX, moneyCurrentY, currentX, moneyCurrentY)
                }
                drawPath(path = moneySolidPath, color = forecastGreen, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                // 2. Dashed Green Money Curve (Today -> End of cycle)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f), 0f)
                val moneyDashedPath = Path().apply {
                    moveTo(currentX, moneyCurrentY)
                    val endX = leftPadding + chartWidthPx
                    val midX = (currentX + endX) / 2
                    cubicTo(midX, moneyCurrentY, midX, moneyEndY, endX, moneyEndY)
                }
                drawPath(path = moneyDashedPath, color = forecastGreen, style = Stroke(width = 2.5.dp.toPx(), pathEffect = dashEffect, cap = StrokeCap.Round))

                // 3. Solid Charcoal Expense Curve (0 -> Today)
                val spendSolidPath = Path().apply {
                    moveTo(leftPadding, spendStartY)
                    val midX = (leftPadding + currentX) / 2
                    cubicTo(midX, spendStartY, midX, spendCurrentY, currentX, spendCurrentY)
                }
                drawPath(path = spendSolidPath, color = charcoalColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))

                // 4. Dashed Blue Plan Expense Curve (Today -> End of cycle)
                val spendDashedPath = Path().apply {
                    moveTo(currentX, spendCurrentY)
                    val endX = leftPadding + chartWidthPx
                    val midX = (currentX + endX) / 2
                    cubicTo(midX, spendCurrentY, midX, spendProjectedEndY, endX, spendProjectedEndY)
                }
                drawPath(path = spendDashedPath, color = planBlue, style = Stroke(width = 3.dp.toPx(), pathEffect = dashEffect, cap = StrokeCap.Round))

                // Interpolation function
                fun getBezierYForX(targetX: Float, x0: Float, y0: Float, x3: Float, y3: Float): Float {
                    if (targetX <= x0) return y0
                    if (targetX >= x3) return y3
                    val t = (targetX - x0) / (x3 - x0)
                    val u = 1 - t
                    val y1 = y0
                    val y2 = y3
                    return (u * u * u * y0) + (3 * u * u * t * y1) + (3 * u * t * t * y2) + (t * t * t * y3)
                }
                
                fun getCurveYForX(targetX: Float, isMoney: Boolean): Float {
                    if (targetX <= currentX) {
                        val startY = if (isMoney) moneyStartY else spendStartY
                        val endY = if (isMoney) moneyCurrentY else spendCurrentY
                        return getBezierYForX(targetX, leftPadding, startY, currentX, endY)
                    } else {
                        val startY = if (isMoney) moneyCurrentY else spendCurrentY
                        val endY = if (isMoney) moneyEndY else spendProjectedEndY
                        val endX = leftPadding + chartWidthPx
                        return getBezierYForX(targetX, currentX, startY, endX, endY)
                    }
                }

                // Draw vertical line at today OR at touch position
                val verticalDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                if (touchX == null) {
                    drawLine(
                        color = verticalGuideColor,
                        start = Offset(currentX, topPadding),
                        end = Offset(currentX, height - bottomPadding),
                        strokeWidth = 1.5f,
                        pathEffect = verticalDash
                    )
                    drawCircle(color = forecastGreen, radius = 4.5.dp.toPx(), center = Offset(currentX, moneyCurrentY))
                    drawCircle(color = charcoalColor, radius = 4.5.dp.toPx(), center = Offset(currentX, spendCurrentY))
                } else {
                    // INTERACTIVE MODE
                    val safeX = touchX!!.coerceIn(leftPadding, leftPadding + chartWidthPx)
                    
                    drawLine(
                        color = primaryColor,
                        start = Offset(safeX, topPadding),
                        end = Offset(safeX, height - bottomPadding),
                        strokeWidth = 2f
                    )
                    
                    val touchMoneyY = getCurveYForX(safeX, true)
                    val touchSpendY = getCurveYForX(safeX, false)
                    
                    drawCircle(color = forecastGreen, radius = 5.dp.toPx(), center = Offset(safeX, touchMoneyY))
                    drawCircle(color = if (safeX <= currentX) charcoalColor else planBlue, radius = 5.dp.toPx(), center = Offset(safeX, touchSpendY))
                    
                    // Update state for tooltip
                    tooltipMoney = toValue(touchMoneyY)
                    tooltipSpend = toValue(touchSpendY)
                    tooltipIsFuture = safeX > currentX
                }
            }

            // Interactive Tooltip Overlay
            if (touchX != null) {
                val safeX = touchX!!.coerceIn(leftPadPx, leftPadPx + chartWidthPx)
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = surfaceVariantColor,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = with(density) { (safeX - 60.dp.toPx()).toDp().coerceIn(leftPadPx.toDp(), (leftPadPx + chartWidthPx - 130.dp.toPx()).toDp()) },
                                y = 0.dp
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("Свободно: ${CurrencyHelper.formatAmount(tooltipMoney, currency)}", style = MaterialTheme.typography.labelSmall, color = forecastGreen, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Расходы: ${CurrencyHelper.formatAmount(tooltipSpend, currency)}", style = MaterialTheme.typography.labelSmall, color = if (!tooltipIsFuture) charcoalColor else planBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Left Y-Axis Numbers
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 6.dp, top = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${topTick / 1000} 000",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Text(
                    text = "${midTick / 1000} 000",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Category Row in "Расходы" with dual bar (solid black + dotted blue) matching Screenshot 2
 */
@Composable
fun ZenCategoryPlanRow(
    category: CategoryEntity,
    spentAmount: Double,
    forecastAmount: Double,
    hasCustomBudget: Boolean,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = IconHelper.parseColor(category.colorHex)
    val ratio = if (forecastAmount > 0) (spentAmount / forecastAmount).toFloat().coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp)
            .testTag("zen_category_plan_row_${category.name}")
    ) {
        // Upper Line: Icon + Category Name ▾ + Spent Amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconHelper.getIconByName(category.iconName),
                        contentDescription = category.name,
                        tint = categoryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = CurrencyHelper.formatAmount(spentAmount, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dual Progress Bar: Solid Charcoal (spent) + Dotted Blue (remaining to forecast)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp)
                .height(4.dp)
        ) {
            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val solidW = (w * ratio).coerceIn(0f, w)

                // Solid line for spent
                if (solidW > 0) {
                    drawLine(
                        color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF2C3437),
                        start = Offset(0f, h / 2),
                        end = Offset(solidW, h / 2),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Dotted blue line extending forward to the forecast limit
                if (solidW < w) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    drawLine(
                        color = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF1E88E5),
                        start = Offset(solidW, h / 2),
                        end = Offset(w, h / 2),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = dashEffect,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Forecast Caption: 📈 прогноз 42 000 ₽
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "прогноз ${CurrencyHelper.formatAmount(forecastAmount, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (hasCustomBudget) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MoneySubRow(
    title: String,
    amount: Double,
    currency: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = CurrencyHelper.formatAmount(amount, currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Modal Dialog: "Планы в Дзене" matching Screenshot 1
 */
@Composable
fun ZenPlansInfoModal(
    onDismiss: () -> Unit,
    onOpenAiAudit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("zen_plans_info_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top drag handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Red/Pink Calendar Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Планы в Дзене",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Полная картина ваших финансов\nв ближайшем будущем",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mini preview chart card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Guideline
                        drawLine(
                            color = Color(0xFFE0E0E0),
                            start = Offset(w * 0.55f, 10f),
                            end = Offset(w * 0.55f, h - 10f),
                            strokeWidth = 1f
                        )
                        // Green line
                        val gPath = Path().apply {
                            moveTo(20f, h * 0.7f)
                            lineTo(40f, h * 0.35f)
                            lineTo(w * 0.55f, h * 0.35f)
                            lineTo(w - 20f, h * 0.2f)
                        }
                        drawPath(gPath, Color(0xFF34A853), style = Stroke(width = 3.dp.toPx()))
                        // Charcoal line
                        val cPath = Path().apply {
                            moveTo(20f, h * 0.85f)
                            lineTo(w * 0.3f, h * 0.6f)
                            lineTo(w * 0.55f, h * 0.45f)
                            lineTo(w - 20f, h * 0.38f)
                        }
                        drawPath(cPath, Color(0xFF2C3437), style = Stroke(width = 3.dp.toPx()))
                        // Dots
                        drawCircle(Color(0xFF34A853), 4.dp.toPx(), Offset(w * 0.55f, h * 0.35f))
                        drawCircle(Color(0xFF2C3437), 4.dp.toPx(), Offset(w * 0.55f, h * 0.45f))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // "Планы учитывают:"
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Планы учитывают",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34A853))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Все ваши деньги в этом месяце:",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• баланс на начало месяца\n• добавленные вручную планы поступлений\n• прогнозы поступлений от нашего AI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C3437))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Все расходы:",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• планируемые платежи\n• прогнозируемые расходы по категориям",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Advantage: Free in ManiMani!
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "В приложении Мани-мани функция Планы доступна полностью бесплатно и без подписок!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Понятно", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dialog: List of Planned Payments & Incomes (opened by [📅 count])
 */
@Composable
fun ZenPlannedPaymentsDialog(
    items: List<com.example.data.entity.PlannedTransactionEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit,
    onDeleteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit,
    onExecuteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("zen_planned_payments_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ожидаемые операции",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onAddItem,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (items.isEmpty()) {
                    Text(
                        text = "Пока нет запланированных платежей",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    items.forEach { item ->
                        val cal = Calendar.getInstance().apply { timeInMillis = item.plannedDate }
                        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                        val isIncome = item.type == "INCOME"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditItem(item) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Colored date dot
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isIncome) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${dayOfMonth}",
                                    color = if (isIncome) IncomeGreen else ExpenseRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.note,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isIncome) "Доход" else "Расход",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = (if (isIncome) "+" else "-") + CurrencyHelper.formatAmount(item.amount, currency),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(onClick = { onExecuteItem(item) }) {
                                Icon(Icons.Default.Check, contentDescription = "Исполнить", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteItem(item) }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

/**
 * Dialog to add a planned payment or income
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddPlannedPaymentDialog(
    existingItem: com.example.data.entity.PlannedTransactionEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, isIncome: Boolean, day: Int, reminderType: String) -> Unit
) {
    var title by remember { mutableStateOf(existingItem?.note ?: "") }
    var amountText by remember { mutableStateOf(existingItem?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var isIncome by remember { mutableStateOf(existingItem?.type == "INCOME") }
    var dayText by remember { 
        mutableStateOf(
            existingItem?.plannedDate?.let { 
                Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH).toString() 
            } ?: "15"
        )
    }
    var reminderType by remember { mutableStateOf(existingItem?.reminderType ?: "NONE") }
    var showReminderDropdown by remember { mutableStateOf(false) }
    
    val reminderLabels = mapOf(
        "NONE" to "Не напоминать",
        "ON_DAY" to "В день платежа (утром)",
        "1_DAY_BEFORE" to "За 1 день",
        "3_DAYS_BEFORE" to "За 3 дня"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("add_planned_payment_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Запланировать операцию",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Type switcher: Расход / Доход
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !isIncome,
                        onClick = { isIncome = false },
                        label = { Text("Расход") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = isIncome,
                        onClick = { isIncome = true },
                        label = { Text("Доход") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название (например: Аренда, Зарплата)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Сумма") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("День месяца (1..31)") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                ExposedDropdownMenuBox(
                    expanded = showReminderDropdown,
                    onExpandedChange = { showReminderDropdown = !showReminderDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = reminderLabels[reminderType] ?: "Не напоминать",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Напоминание") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showReminderDropdown) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showReminderDropdown,
                        onDismissRequest = { showReminderDropdown = false }
                    ) {
                        reminderLabels.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    reminderType = key
                                    showReminderDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            val day = dayText.toIntOrNull() ?: 15
                            if (title.isBlank() || amount <= 0 || day !in 1..31) return@Button
                            onConfirm(title.trim(), amount, isIncome, day, reminderType)
                        }
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

/**
 * Quick dialog to edit/set budget plan for a category
 */
@Composable
fun EditCategoryPlanDialog(
    category: CategoryEntity,
    currentLimit: Double?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var limitText by remember { mutableStateOf(currentLimit?.toInt()?.toString() ?: "30000") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("edit_category_plan_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "План на категорию: ${category.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Задайте плановый лимит расходов на расчетный период. Он отобразится на графике прогноза.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter { c -> c.isDigit() } },
                    label = { Text("Сумма прогноза/плана ($currency)") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentLimit != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Удалить")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val amount = limitText.toDoubleOrNull() ?: 0.0
                                if (amount > 0) {
                                    onSave(amount)
                                }
                            },
                            enabled = (limitText.toDoubleOrNull() ?: 0.0) > 0
                        ) {
                            Text("Сохранить")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick Add Plan Dialog triggered by FAB
 */
@Composable
fun QuickAddPlanDialog(
    categories: List<CategoryEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onSelectCategory: (CategoryEntity) -> Unit,
    onAddPlannedPayment: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("quick_add_plan_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Добавить в планы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: Add scheduled operation
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddPlannedPayment() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Запланировать платеж или доход", fontWeight = FontWeight.SemiBold)
                            Text("Аренда, зарплата, подписки с датой", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Или задать прогноз на категорию:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(categories) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCategory(cat) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(IconHelper.parseColor(cat.colorHex).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = IconHelper.getIconByName(cat.iconName),
                                    contentDescription = null,
                                    tint = IconHelper.parseColor(cat.colorHex),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExecutePlanDialog(
    item: com.example.data.entity.PlannedTransactionEntity,
    accounts: List<com.example.data.entity.AccountEntity>,
    categories: List<com.example.data.entity.CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (accountId: Long, categoryId: Long?) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: -1L) }
    var selectedCategoryId by remember { mutableStateOf(item.categoryId) }
    
    var showAccountDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    
    val selectedAccountName = accounts.find { it.id == selectedAccountId }?.name ?: "Выберите счет"
    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Без категории"
    val isIncome = item.type == "INCOME"

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("execute_plan_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Исполнить операцию",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "${item.note} (${item.amount})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Account Selection
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = showAccountDropdown,
                    onExpandedChange = { showAccountDropdown = !showAccountDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedAccountName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Счет списания/зачисления") },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAccountDropdown) },
                        colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showAccountDropdown,
                        onDismissRequest = { showAccountDropdown = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedAccountId = acc.id
                                    showAccountDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Selection
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = !showCategoryDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категория") },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                        colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        val filteredCategories = categories.filter { it.type == (if (isIncome) "INCOME" else "EXPENSE") }
                        filteredCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(selectedAccountId, selectedCategoryId)
                        },
                        enabled = selectedAccountId != -1L
                    ) {
                        Text("Исполнить")
                    }
                }
            }
        }
    }
}
