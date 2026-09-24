package com.example.ui.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.dao.PeriodSummaryResult
import com.example.data.entity.TransactionEntity
import com.example.ui.components.TransactionItemCard
import com.example.ui.viewmodel.FinanceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: FinanceUiState,
    historyTransactions: List<TransactionEntity> = state.transactions,
    historySummary: PeriodSummaryResult = PeriodSummaryResult(
        totalCount = historyTransactions.size,
        totalExpense = historyTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    ),
    selectedPeriod: HistoryPeriodType = HistoryPeriodType.WEEK,
    customRange: Pair<Long, Long> = Pair(System.currentTimeMillis() - 30L * 86_400_000L, System.currentTimeMillis()),
    onPeriodSelected: (HistoryPeriodType) -> Unit = {},
    onCustomRangeSelected: (Long, Long) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
    onDeleteTransaction: (TransactionEntity) -> Unit = {},
    onEditTransaction: (TransactionEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, EXPENSE, INCOME, TRANSFER
    var selectedAccountIdFilter by remember { mutableStateOf<Long?>(null) }
    var showDateRangeDialog by remember { mutableStateOf(false) }

    val accountsMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val goalsMap = remember(state.goals) { state.goals.associateBy { it.id } }
    val debtsMap = remember(state.debts) { state.debts.associateBy { it.id } }
    val categoriesMap = remember(state.categories) { state.categories.associateBy { it.id } }

    val filteredTransactions = remember(
        historyTransactions,
        searchQuery,
        selectedTypeFilter,
        selectedAccountIdFilter
    ) {
        historyTransactions.filter { tx ->
            val matchesType = when (selectedTypeFilter) {
                "EXPENSE" -> tx.type == "EXPENSE"
                "INCOME" -> tx.type == "INCOME"
                "TRANSFER" -> tx.type == "TRANSFER"
                else -> true
            }

            val matchesAccount = selectedAccountIdFilter == null ||
                    tx.accountId == selectedAccountIdFilter ||
                    tx.toAccountId == selectedAccountIdFilter

            val category = tx.categoryId?.let { categoriesMap[it] }
            val account = accountsMap[tx.accountId]

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                tx.note.lowercase().contains(q) ||
                tx.tag.lowercase().contains(q) ||
                (category?.name?.lowercase()?.contains(q) == true) ||
                (account?.name?.lowercase()?.contains(q) == true) ||
                tx.amount.toString().contains(q)
            }

            matchesType && matchesAccount && matchesSearch
        }
    }

    val listState = rememberLazyListState()

    // Lazy load next page when scrolled near the bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 5 && historyTransactions.size < historySummary.totalCount
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    // Date range picker dialog for CUSTOM period
    if (showDateRangeDialog) {
        HistoryDateRangePickerDialog(
            initialStartDate = customRange.first,
            initialEndDate = customRange.second,
            onDismissRequest = { showDateRangeDialog = false },
            onConfirm = { start, end ->
                showDateRangeDialog = false
                onCustomRangeSelected(start, end)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen")
    ) {
        // 1. Top Bar: "История" + count badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "История",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )

            // Total count badge in Zen style
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = HistoryPeriodHelper.formatOperationsCount(filteredTransactions.size),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // 2. Period Switcher (Segmented Control: Неделя | Месяц | Всё время | Период)
        HistoryPeriodSegmentedControl(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = { period ->
                if (period == HistoryPeriodType.CUSTOM) {
                    showDateRangeDialog = true
                } else {
                    onPeriodSelected(period)
                }
            }
        )

        // 3. Period Summary (Header / Count / Sum)
        val periodTitle = remember(selectedPeriod, customRange) {
            HistoryPeriodHelper.formatPeriodLabel(
                type = selectedPeriod,
                customStart = customRange.first,
                customEnd = customRange.second
            )
        }

        HistoryPeriodSummaryCard(
            periodTitle = periodTitle,
            totalCount = historySummary.totalCount,
            totalExpense = historySummary.totalExpense,
            baseCurrency = state.baseCurrency,
            isCustomPeriod = selectedPeriod == HistoryPeriodType.CUSTOM,
            onEditCustomRange = { showDateRangeDialog = true }
        )

        // 4. Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .testTag("search_transactions_input"),
            placeholder = { Text("Поиск по заметке, тегу, категории...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Поиск")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        // 5. Filter Chips Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val types = listOf(
                "ALL" to "Все",
                "EXPENSE" to "Расходы",
                "INCOME" to "Доходы",
                "TRANSFER" to "Переводы"
            )
            items(types) { (key, label) ->
                FilterChip(
                    selected = selectedTypeFilter == key,
                    onClick = { selectedTypeFilter = key },
                    label = { Text(label) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("filter_chip_$key")
                )
            }

            // Account filters
            item {
                VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 4.dp))
            }

            items(state.accounts.filter { !it.isArchived }) { acc ->
                val isSelected = selectedAccountIdFilter == acc.id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedAccountIdFilter = if (isSelected) null else acc.id
                    },
                    label = { Text(acc.name) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // 6. Transaction List or Empty State
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (historySummary.totalCount == 0) {
                        Text(
                            text = "За выбранный период операций нет",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Выберите другой период или добавьте операцию",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Операции не найдены",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Попробуйте изменить параметры поиска или фильтры",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    TransactionItemCard(
                        transaction = tx,
                        accountsMap = accountsMap,
                        categoriesMap = categoriesMap,
                        goalsMap = goalsMap,
                        debtsMap = debtsMap,
                        onDelete = onDeleteTransaction,
                        onClick = { onEditTransaction(tx) }
                    )
                }
            }
        }
    }
}
