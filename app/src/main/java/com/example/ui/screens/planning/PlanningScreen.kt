package com.example.ui.screens.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.entity.BudgetEntity
import com.example.data.entity.DebtEntity
import com.example.data.entity.GoalEntity
import com.example.service.gemini.AiPromptType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper
import com.example.ui.viewmodel.FinanceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(
    state: FinanceUiState,
    onAddBudget: (categoryId: Long?, limitAmount: Double) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    onAddGoal: (name: String, target: Double, current: Double, colorHex: String, iconName: String) -> Unit,
    onEditGoal: (GoalEntity) -> Unit = {},
    onContributeGoal: (goalId: Long, amount: Double) -> Unit,
    onDeleteGoal: (GoalEntity) -> Unit,
    onAddDebt: (person: String, amount: Double, isOwedToMe: Boolean, note: String) -> Unit,
    onEditDebt: (DebtEntity) -> Unit = {},
    onToggleDebt: (DebtEntity) -> Unit,
    onDeleteDebt: (DebtEntity) -> Unit,
    onAddPlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onUpdatePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onAddTransaction: (com.example.data.entity.TransactionEntity) -> Unit = {},
    onOpenGeminiAssistant: (AiPromptType) -> Unit = {},
    onOpenPaydaySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Планы", "Копилки", "Долги")

    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var goalToEdit by remember { mutableStateOf<GoalEntity?>(null) }
    var showContributeGoalDialog by remember { mutableStateOf<GoalEntity?>(null) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var debtToEdit by remember { mutableStateOf<DebtEntity?>(null) }

    when (selectedTab) {
        0 -> {
            ZenPlansMainView(
                state = state,
                onAddBudget = onAddBudget,
                onDeleteBudget = onDeleteBudget,
                onAddPlannedTransaction = onAddPlannedTransaction,
                onUpdatePlannedTransaction = onUpdatePlannedTransaction,
                onDeletePlannedTransaction = onDeletePlannedTransaction,
                selectedSubTab = selectedTab,
                onSubTabSelected = { selectedTab = it },
                subTabs = tabs,
                onOpenPaydaySettings = onOpenPaydaySettings,
                onOpenGeminiAssistant = onOpenGeminiAssistant,
                modifier = modifier
            )
        }
        1, 2 -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("planning_screen")
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Unified Top Bar: Matches ZenPlansMainView top bar perfectly
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
                                IconButton(
                                    onClick = { onOpenGeminiAssistant(AiPromptType.BUDGETS_AND_GOALS) },
                                    modifier = Modifier.testTag("planning_gemini_action_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "ИИ анализ бюджетов и целей",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

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

                    // Unified Sub-tabs segment switcher: [ Планы | Копилки | Долги ]
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val isSelected = selectedTab == index
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedTab = index }
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

                    if (selectedTab == 1) {
                        item {
                            Button(
                                onClick = { showAddGoalDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .height(48.dp)
                                    .testTag("add_goal_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Новая цель накопления", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (state.goals.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Копилок пока нет",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Создайте цель накопления на отпуск, ноутбук или автомобиль",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(state.goals, key = { it.id }) { goal ->
                                val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                                val percentInt = (progress * 100).toInt()
                                val goalColor = IconHelper.parseColor(goal.colorHex)

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .clickable { goalToEdit = goal }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(goalColor.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = IconHelper.getIconByName(goal.iconName),
                                                        contentDescription = null,
                                                        tint = goalColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = goal.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Собрано $percentInt%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = IncomeGreen
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Button(
                                                    onClick = { showContributeGoalDialog = goal },
                                                    modifier = Modifier.height(34.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Внести", fontSize = 12.sp)
                                                }
                                                IconButton(
                                                    onClick = { onDeleteGoal(goal) },
                                                    modifier = Modifier.size(28.dp).padding(start = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Удалить цель",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = goalColor,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Накоплено: ${CurrencyHelper.formatAmount(goal.currentAmount, state.baseCurrency)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Цель: ${CurrencyHelper.formatAmount(goal.targetAmount, state.baseCurrency)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedTab == 2) {
                        // Summary Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    color = IncomeGreen.copy(alpha = 0.12f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Мне должны", style = MaterialTheme.typography.labelSmall, color = IncomeGreen)
                                        Text(
                                            CurrencyHelper.formatAmount(state.totalOwedToMe, state.baseCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = IncomeGreen
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    color = ExpenseRed.copy(alpha = 0.12f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Я должен", style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
                                        Text(
                                            CurrencyHelper.formatAmount(state.totalIOwe, state.baseCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ExpenseRed
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = { showAddDebtDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .height(48.dp)
                                    .testTag("add_debt_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Добавить долг или займ", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (state.debts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Handshake,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Долгов нет",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Учитывайте займы друзьям, коллегам или свои кредиты",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(state.debts, key = { it.id }) { debt ->
                                val badgeColor = if (debt.isOwedToMe) IncomeGreen else ExpenseRed

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (debt.isSettled) 0.2f else 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .clickable { debtToEdit = debt }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = debt.isSettled,
                                            onCheckedChange = { onToggleDebt(debt) }
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = debt.personName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = badgeColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = if (debt.isOwedToMe) "Мне должны" else "Я должен",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (debt.note.isNotBlank()) {
                                                Text(
                                                    text = debt.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (debt.isSettled) {
                                                Text(
                                                    text = "Долг закрыт ✓",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = IncomeGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Text(
                                            text = CurrencyHelper.formatAmount(debt.amount, state.baseCurrency),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (debt.isSettled) MaterialTheme.colorScheme.onSurfaceVariant else badgeColor
                                        )

                                        IconButton(
                                            onClick = { onDeleteDebt(debt) },
                                            modifier = Modifier.size(28.dp).padding(start = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Удалить долг",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Budget Dialog
    if (showAddBudgetDialog) {
        AddBudgetDialog(
            categories = state.categories.filter { it.type == "EXPENSE" },
            onDismiss = { showAddBudgetDialog = false },
            onConfirm = { catId, limit ->
                onAddBudget(catId, limit)
                showAddBudgetDialog = false
            }
        )
    }

    // Add Goal Dialog
    if (showAddGoalDialog) {
        AddGoalDialog(
            onDismiss = { showAddGoalDialog = false },
            onConfirm = { name, target, current, color, icon ->
                onAddGoal(name, target, current, color, icon)
                showAddGoalDialog = false
            }
        )
    }

    // Contribute to Goal Dialog
    showContributeGoalDialog?.let { goal ->
        ContributeGoalDialog(
            goal = goal,
            currency = state.baseCurrency,
            onDismiss = { showContributeGoalDialog = null },
            onConfirm = { amount ->
                onContributeGoal(goal.id, amount)
                showContributeGoalDialog = null
            }
        )
    }

    // Add Debt Dialog
    if (showAddDebtDialog) {
        AddDebtDialog(
            onDismiss = { showAddDebtDialog = false },
            onConfirm = { person, amount, isOwedToMe, note ->
                onAddDebt(person, amount, isOwedToMe, note)
                showAddDebtDialog = false
            }
        )
    }

    goalToEdit?.let { goal ->
        EditGoalDialog(
            goal = goal,
            onDismiss = { goalToEdit = null },
            onConfirm = { updated ->
                onEditGoal(updated)
                goalToEdit = null
            }
        )
    }

    debtToEdit?.let { debt ->
        EditDebtDialog(
            debt = debt,
            onDismiss = { debtToEdit = null },
            onConfirm = { updated ->
                onEditDebt(updated)
                debtToEdit = null
            }
        )
    }
}

// ------------------- BUDGETS TAB -------------------
@Composable
private fun BudgetsTab(
    state: FinanceUiState,
    onAddBudgetClick: () -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag("budgets_list")
    ) {
        item {
            Button(
                onClick = onAddBudgetClick,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_budget_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Установить лимит расходов", fontWeight = FontWeight.Bold)
            }
        }

        if (state.budgetProgresses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Лимиты пока не заданы",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Установите лимиты на продукты, кафе или транспорт, чтобы контролировать траты",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(state.budgetProgresses, key = { it.budget.id }) { item ->
                val progress = item.percent.coerceIn(0f, 1f)
                val categoryName = item.category?.name ?: "Все категории"
                val catColor = item.category?.let { IconHelper.parseColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(catColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.category?.let { IconHelper.getIconByName(it.iconName) } ?: Icons.Default.Category,
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = categoryName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.isOverBudget) {
                                        Text(
                                            text = "Превышение бюджета!",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ExpenseRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "Осталось ${CurrencyHelper.formatAmount(item.limit - item.spent, state.baseCurrency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IncomeGreen
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onDeleteBudget(item.budget) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Удалить бюджет",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (item.isOverBudget) ExpenseRed else catColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Потрачено: ${CurrencyHelper.formatAmount(item.spent, state.baseCurrency)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Лимит: ${CurrencyHelper.formatAmount(item.limit, state.baseCurrency)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}



// ------------------- POPUPS / DIALOGS -------------------

@Composable
fun AddBudgetDialog(
    categories: List<com.example.data.entity.CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: Long?, limitAmount: Double) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
    var limitText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Установить бюджет", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.replace(',', '.'); errorMessage = null },
                    label = { Text("Сумма лимита в месяц") },
                    placeholder = { Text("15000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Категория расходов:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))

                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Все расходы") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            val limit = limitText.toDoubleOrNull()
                            if (limit == null || limit <= 0) {
                                errorMessage = "Введите сумму больше нуля"
                                return@Button
                            }
                            onConfirm(selectedCategoryId, limit)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, target: Double, current: Double, color: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var currentText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Новая цель накопления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("На что копим?") },
                    placeholder = { Text("Отпуск, новый ноутбук...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.replace(',', '.'); errorMessage = null },
                    label = { Text("Целевая сумма") },
                    placeholder = { Text("100000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it.replace(',', '.') },
                    label = { Text("Уже накоплено (опционально)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Укажите название цели"
                                return@Button
                            }
                            val target = targetText.toDoubleOrNull()
                            if (target == null || target <= 0) {
                                errorMessage = "Укажите целевую сумму больше нуля"
                                return@Button
                            }
                            val current = currentText.toDoubleOrNull() ?: 0.0
                            onConfirm(name.trim(), target, current, "#10B981", "flag")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Создать") }
                }
            }
        }
    }
}

@Composable
fun ContributeGoalDialog(
    goal: GoalEntity,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Пополнить цель «${goal.name}»", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.replace(',', '.'); errorMessage = null },
                    label = { Text("Сумма пополнения") },
                    placeholder = { Text("5000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                errorMessage = "Введите сумму больше нуля"
                                return@Button
                            }
                            onConfirm(amount)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Внести") }
                }
            }
        }
    }
}

@Composable
fun AddDebtDialog(
    onDismiss: () -> Unit,
    onConfirm: (person: String, amount: Double, isOwedToMe: Boolean, note: String) -> Unit
) {
    var personName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isOwedToMe by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Запись о долге", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOwedToMe) IncomeGreen else Color.Transparent)
                            .clickable { isOwedToMe = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Мне должны",
                            color = if (isOwedToMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isOwedToMe) ExpenseRed else Color.Transparent)
                            .clickable { isOwedToMe = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Я должен",
                            color = if (!isOwedToMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it; errorMessage = null },
                    label = { Text("Имя человека / организация") },
                    placeholder = { Text("Алексей или Банк") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.replace(',', '.'); errorMessage = null },
                    label = { Text("Сумма долга") },
                    placeholder = { Text("5000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Комментарий (опционально)") },
                    placeholder = { Text("За обед, аренда...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            if (personName.isBlank()) {
                                errorMessage = "Укажите имя или организацию"
                                return@Button
                            }
                            val amount = amountText.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                errorMessage = "Введите сумму больше нуля"
                                return@Button
                            }
                            onConfirm(personName.trim(), amount, isOwedToMe, note.trim())
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}


@Composable
fun EditGoalDialog(
    goal: com.example.data.entity.GoalEntity,
    onDismiss: () -> Unit,
    onConfirm: (com.example.data.entity.GoalEntity) -> Unit
) {
    var name by remember { mutableStateOf(goal.name) }
    var targetText by remember { mutableStateOf(goal.targetAmount.toString()) }
    var currentText by remember { mutableStateOf(goal.currentAmount.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Редактировать копилку", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.replace(',', '.'); errorMessage = null },
                    label = { Text("Целевая сумма") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it.replace(',', '.') },
                    label = { Text("Уже накоплено") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Укажите название"
                                return@Button
                            }
                            val target = targetText.toDoubleOrNull()
                            if (target == null || target <= 0) {
                                errorMessage = "Неверная сумма"
                                return@Button
                            }
                            val current = currentText.toDoubleOrNull() ?: 0.0
                            onConfirm(goal.copy(name = name.trim(), targetAmount = target, currentAmount = current))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}


@Composable
fun EditDebtDialog(
    debt: com.example.data.entity.DebtEntity,
    onDismiss: () -> Unit,
    onConfirm: (com.example.data.entity.DebtEntity) -> Unit
) {
    var personName by remember { mutableStateOf(debt.personName) }
    var amountText by remember { mutableStateOf(debt.amount.toString()) }
    var isOwedToMe by remember { mutableStateOf(debt.isOwedToMe) }
    var note by remember { mutableStateOf(debt.note) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Редактировать долг", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isOwedToMe, onClick = { isOwedToMe = true })
                        Text("Мне должны", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !isOwedToMe, onClick = { isOwedToMe = false })
                        Text("Я должен", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it; errorMessage = null },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.replace(',', '.'); errorMessage = null },
                    label = { Text("Сумма") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            if (personName.isBlank()) {
                                errorMessage = "Укажите имя"
                                return@Button
                            }
                            val amt = amountText.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                errorMessage = "Неверная сумма"
                                return@Button
                            }
                            onConfirm(debt.copy(personName = personName.trim(), amount = amt, isOwedToMe = isOwedToMe))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}
