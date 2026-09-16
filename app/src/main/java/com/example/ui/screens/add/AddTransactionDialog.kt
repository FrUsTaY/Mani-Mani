package com.example.ui.screens.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.AccountEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.GoalEntity
import com.example.data.entity.DebtEntity
import com.example.service.UserBankHelper
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    goals: List<GoalEntity> = emptyList(),
    debts: List<DebtEntity> = emptyList(),
    bankOfTheMonth: String = "VTB",
    transactionToEdit: TransactionEntity? = null,
    onDismiss: () -> Unit,
    onManageCategories: () -> Unit = {},
    onConfirm: (
        type: String,
        amount: Double,
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        note: String,
        tag: String,
        excludeFromStats: Boolean,
        goalId: Long?,
        debtId: Long?
    ) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    var selectedType by remember {
        mutableStateOf(transactionToEdit?.type ?: "EXPENSE")
    } // EXPENSE, INCOME, TRANSFER

    var amountText by remember {
        mutableStateOf(
            transactionToEdit?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }

    var selectedAccountId by remember {
        mutableStateOf(
            transactionToEdit?.accountId ?: activeAccounts.firstOrNull()?.id ?: 0L
        )
    }

    var selectedToAccountId by remember {
        mutableStateOf(
            if (transactionToEdit?.goalId != null) 0L else transactionToEdit?.toAccountId ?: activeAccounts.getOrNull(1)?.id ?: activeAccounts.firstOrNull()?.id ?: 0L
        )
    }

    var selectedGoalId by remember {
        mutableStateOf(transactionToEdit?.goalId)
    }
    var selectedDebtId by remember {
        mutableStateOf(transactionToEdit?.debtId)
    }

    val filteredCategories = remember(selectedType, categories) {
        categories.filter { it.type == selectedType }
    }

    var selectedCategoryId by remember {
        mutableStateOf(
            if (transactionToEdit?.debtId != null) null else transactionToEdit?.categoryId ?: filteredCategories.firstOrNull()?.id
        )
    }

    // Update selected category when type changes (skip on first load if editing)
    var isFirstTypeSelection by remember { mutableStateOf(transactionToEdit == null) }
    LaunchedEffect(selectedType) {
        if (!isFirstTypeSelection) {
            isFirstTypeSelection = true
            return@LaunchedEffect
        }
        val firstCat = categories.firstOrNull { it.type == selectedType }
        selectedCategoryId = firstCat?.id
    }

    var noteText by remember { mutableStateOf(transactionToEdit?.note ?: "") }
    var tagText by remember { mutableStateOf(transactionToEdit?.tag ?: "") }
    var excludeFromStats by remember { mutableStateOf(transactionToEdit?.excludeFromStats ?: false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Smart Bank recommendation based on user rules
    val bankSuggestion = remember(selectedType, selectedCategoryId, noteText, bankOfTheMonth, activeAccounts, categories) {
        UserBankHelper.suggestAccount(
            accounts = activeAccounts,
            categories = categories,
            type = selectedType,
            categoryId = selectedCategoryId,
            note = noteText,
            bankOfTheMonth = bankOfTheMonth
        )
    }

    val bankSuggestionReason = remember(bankSuggestion, selectedType, noteText, bankOfTheMonth) {
        when {
            noteText.contains("пятёрочк", ignoreCase = true) || noteText.contains("пятерочк", ignoreCase = true) ->
                "Апельсиновая карта для покупок в магазине Пятёрочка"
            selectedType == "INCOME" && (noteText.contains("жен", ignoreCase = true) || noteText.contains("налич", ignoreCase = true)) ->
                "Шлюз для наличной зарплаты жены через банкомат"
            bankOfTheMonth == "YANDEX" && bankSuggestion?.name?.contains("Яндекс", ignoreCase = true) == true ->
                "Яндекс Банк выбран банком месяца под повышенный кэшбэк"
            bankSuggestion?.name?.contains("Продукт", ignoreCase = true) == true ->
                "Основной счёт ВТБ для покупки продуктов"
            bankSuggestion?.name?.contains("Зарплат", ignoreCase = true) == true || bankSuggestion?.name?.contains("ЗП", ignoreCase = true) == true ->
                "Счёт остатков ЗП для покупок и платежей"
            else -> "Рекомендованный счёт по вашим правилам"
        }
    }

    // Auto-select suggested account if strong keyword matches (only if adding a new transaction)
    LaunchedEffect(bankSuggestion?.id) {
        if (transactionToEdit == null) {
            bankSuggestion?.id?.let { suggestedId ->
                if (noteText.contains("пятёрочк", ignoreCase = true) ||
                    noteText.contains("пятерочк", ignoreCase = true) ||
                    noteText.contains("апельсин", ignoreCase = true) ||
                    (selectedType == "INCOME" && noteText.contains("жен", ignoreCase = true))
                ) {
                    selectedAccountId = suggestedId
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .testTag("add_transaction_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (transactionToEdit == null) "Новая операция" else "Редактировать операцию",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_add_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type Segmented Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val types = listOf(
                        Triple("EXPENSE", "Расход", ExpenseRed),
                        Triple("INCOME", "Доход", IncomeGreen),
                        Triple("TRANSFER", "Перевод", TransferBlue)
                    )
                    types.forEach { (typeKey, label, color) ->
                        val isSelected = selectedType == typeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) color else Color.Transparent)
                                .clickable { selectedType = typeKey }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Amount Input
                val currentAccount = accounts.find { it.id == selectedAccountId }
                val currencySymbol = CurrencyHelper.currencySymbols[currentAccount?.currency ?: "RUB"] ?: "₽"

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*([.,]\d{0,2})?$"""))) {
                            amountText = input.replace(',', '.')
                            errorMessage = null
                        }
                    },
                    label = { Text("Сумма операции") },
                    placeholder = { Text("0.00") },
                    trailingIcon = {
                        Text(
                            text = currencySymbol,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = when (selectedType) {
                            "EXPENSE" -> ExpenseRed
                            "INCOME" -> IncomeGreen
                            else -> TransferBlue
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("amount_input_field"),
                    shape = RoundedCornerShape(16.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Smart Bank Suggestion Banner
                if (bankSuggestion != null && selectedType != "TRANSFER") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAccountId = bankSuggestion.id
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Рекомендован: ${bankSuggestion.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = bankSuggestionReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selectedAccountId != bankSuggestion.id) {
                                TextButton(
                                    onClick = { selectedAccountId = bankSuggestion.id },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Выбрать")
                                }
                            } else {
                                Text(
                                    text = "Выбран ✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Source Account Selector
                Text(
                    text = if (selectedType == "TRANSFER") "Со счёта" else "Счёт",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(accounts.filter { !it.isArchived }) { acc ->
                        val isSelected = acc.id == selectedAccountId
                        val accColor = IconHelper.parseColor(acc.colorHex)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .clickable { selectedAccountId = acc.id }
                                .testTag("account_chip_${acc.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = CurrencyHelper.formatAmount(acc.balance, acc.currency),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Target Account Selector (if TRANSFER)
                AnimatedVisibility(visible = selectedType == "TRANSFER") {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "На счёт",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // accounts
                            items(accounts.filter { !it.isArchived && it.id != selectedAccountId }) { acc ->
                                val isSelected = acc.id == selectedToAccountId
                                val accColor = IconHelper.parseColor(acc.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                                    modifier = Modifier.clickable { selectedToAccountId = acc.id; selectedGoalId = null }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(accColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = acc.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = CurrencyHelper.formatAmount(acc.balance, acc.currency),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            items(goals) { goal ->
                                val isSelected = goal.id == selectedGoalId
                                val goalColor = IconHelper.parseColor(goal.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                                    modifier = Modifier.clickable { 
                                        selectedToAccountId = 0L 
                                        selectedGoalId = goal.id 
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(goalColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Копилка: " + goal.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = CurrencyHelper.formatAmount(goal.currentAmount, "RUB"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Selector (if not TRANSFER)
                AnimatedVisibility(visible = selectedType != "TRANSFER") {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Категория",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { onManageCategories() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Управление",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Управление",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            items(filteredCategories) { cat ->
                                val isSelected = cat.id == selectedCategoryId
                                val catColor = IconHelper.parseColor(cat.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, catColor) else null,
                                    modifier = Modifier
                                        .clickable { selectedCategoryId = cat.id; selectedDebtId = null }
                                        .testTag("category_chip_${cat.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(catColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconHelper.getIconByName(cat.iconName),
                                                contentDescription = cat.name,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            items(debts) { debt ->
                                val isSelected = debt.id == selectedDebtId
                                val debtColor = MaterialTheme.colorScheme.error
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) debtColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, debtColor) else null,
                                    modifier = Modifier
                                        .clickable { 
                                            selectedCategoryId = null 
                                            selectedDebtId = debt.id 
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(debtColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoneyOff,
                                                contentDescription = debt.personName,
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Долг: " + debt.personName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Заметка / комментарий (опционально)") },
                    placeholder = { Text("Например: покупка продуктов на неделю") },
                    singleLine = true,
                    trailingIcon = {
                        if (noteText.isNotEmpty()) {
                            IconButton(onClick = { noteText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tag
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    label = { Text("Теги (через запятую)") },
                    placeholder = { Text("еда, семья, отпуск") },
                    singleLine = true,
                    trailingIcon = {
                        if (tagText.isNotEmpty()) {
                            IconButton(onClick = { tagText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Exclude switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { excludeFromStats = !excludeFromStats }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Не учитывать в статистике",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Сумма не повлияет на отчёты расходов и доходов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = excludeFromStats,
                        onCheckedChange = { excludeFromStats = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                errorMessage = "Введите корректную сумму больше нуля"
                                return@Button
                            }
                            if (selectedAccountId == 0L) {
                                errorMessage = "Выберите счёт списания"
                                return@Button
                            }
                            if (selectedType == "TRANSFER" && selectedGoalId == null && selectedAccountId == selectedToAccountId) {
                                errorMessage = "Выберите разные счета для перевода"
                                return@Button
                            }
                            if (selectedType == "TRANSFER" && selectedGoalId == null && (selectedToAccountId == null || selectedToAccountId == 0L)) {
                                errorMessage = "Выберите счет зачисления или копилку"
                                return@Button
                            }
                            if (selectedType != "TRANSFER" && selectedCategoryId == null && selectedDebtId == null) {
                                errorMessage = "Выберите категорию или долг"
                                return@Button
                            }

                            // If transferring to a goal, toAccountId is null.
                            val finalToAccountId = if (selectedType == "TRANSFER" && selectedGoalId == null) selectedToAccountId else null
                            // If it's a debt, categoryId is null.
                            val finalCategoryId = if (selectedType != "TRANSFER" && selectedDebtId == null) selectedCategoryId else null
                            
                            onConfirm(
                                selectedType,
                                amount,
                                selectedAccountId,
                                finalToAccountId,
                                finalCategoryId,
                                noteText.trim(),
                                tagText.trim(),
                                excludeFromStats,
                                if (selectedType == "TRANSFER") selectedGoalId else null,
                                if (selectedType != "TRANSFER") selectedDebtId else null
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedType) {
                                "EXPENSE" -> ExpenseRed
                                "INCOME" -> IncomeGreen
                                else -> TransferBlue
                            }
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .testTag("submit_transaction_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (transactionToEdit == null) "Сохранить" else "Сохранить изменения",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
