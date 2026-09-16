package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.entity.AccountEntity
import com.example.data.entity.TransactionEntity
import com.example.service.gemini.AiPromptType
import com.example.ui.components.IncomeDistributionDialog
import com.example.ui.components.Me2MeTransferDialog
import com.example.ui.components.PaydaySettingsDialog
import com.example.ui.screens.GeminiAssistantScreen
import com.example.ui.screens.accounts.AccountsSettingsScreen
import com.example.ui.screens.accounts.AddEditAccountDialog
import com.example.ui.screens.add.AddTransactionDialog
import com.example.ui.screens.analytics.AnalyticsScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.planning.PlanningScreen
import com.example.ui.screens.sync.BankSyncScreen
import com.example.ui.screens.transactions.TransactionsScreen
import com.example.ui.screens.onboarding.WelcomeScreen
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

enum class ManiManiNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Главная", Icons.Default.Home, Icons.Default.Home),
    HISTORY("Операции", Icons.Default.ReceiptLong, Icons.Default.ReceiptLong),
    ANALYTICS("Аналитика", Icons.Default.TrendingUp, Icons.Default.TrendingUp),
    PLANNING("Планы", Icons.Default.CalendarMonth, Icons.Default.CalendarMonth),
    ACCOUNTS("Счета", Icons.Default.AccountBalanceWallet, Icons.Default.AccountBalanceWallet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManiManiApp(
    viewModel: FinanceViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(ManiManiNavTab.HOME) }

    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showBankSyncScreen by remember { mutableStateOf(false) }
    var returnToNotificationSettingsFromBankSync by remember { mutableStateOf(false) }
    var showGeminiAssistantScreen by remember { mutableStateOf(false) }
    var showNotificationSettingsScreen by remember { mutableStateOf(false) }
    var showPaydaySettingsDialog by remember { mutableStateOf(false) }
    var showMe2MeTransferDialog by remember { mutableStateOf(false) }
    var showIncomeDistributionDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Show toast / snackbar on status update
    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearStatus()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("manimani_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!state.isFirstLaunch && !showBankSyncScreen && !showGeminiAssistantScreen && !showNotificationSettingsScreen) {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    ManiManiNavTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // FAB displayed on Home and History tabs for fast access
            AnimatedVisibility(
                visible = !state.isFirstLaunch && !showBankSyncScreen && !showGeminiAssistantScreen && !showNotificationSettingsScreen && (currentTab == ManiManiNavTab.HOME || currentTab == ManiManiNavTab.HISTORY),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { showAddTransactionDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_transaction")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить операцию",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isFirstLaunch) {
                WelcomeScreen(
                    onStartClick = { viewModel.completeFirstLaunch() }
                )
            } else if (showGeminiAssistantScreen) {
                GeminiAssistantScreen(
                    state = state,
                    onBack = { showGeminiAssistantScreen = false },
                    onAskGemini = { type, question -> viewModel.askGemini(type, question) },
                    onClearChat = { viewModel.clearAiChat() },
                    onUpdateInputText = { viewModel.updateAiInputText(it) },
                    onSaveApiKey = { viewModel.saveGeminiApiKey(it) },
                    onTestApiKey = { key, callback -> viewModel.testGeminiApiKey(key, callback) }
                )
            } else if (showNotificationSettingsScreen) {
                val isEveningSummaryEnabled by viewModel.isEveningSummaryEnabledFlow.collectAsStateWithLifecycle()
                val eveningSummaryTime by viewModel.eveningSummaryTimeFlow.collectAsStateWithLifecycle()
                com.example.ui.screens.NotificationSettingsScreen(
                    state = state,
                    onBack = { showNotificationSettingsScreen = false },
                    onTogglePushNotifications = { viewModel.setPushNotificationsEnabled(it) },
                    onSendTestPush = { viewModel.sendTestPushNotification() },
                    onOpenBankSync = {
                        showNotificationSettingsScreen = false
                        showBankSyncScreen = true
                        returnToNotificationSettingsFromBankSync = true
                    },
                    isEveningSummaryEnabled = isEveningSummaryEnabled,
                    onToggleEveningSummary = { viewModel.setEveningSummaryEnabled(it) },
                    eveningSummaryTime = eveningSummaryTime,
                    onSetEveningSummaryTime = { viewModel.setEveningSummaryTime(it) },
                    onToggleBankIntercept = { viewModel.setBankPushInterceptEnabled(it) },
                    onToggleZenmoneyIntercept = { viewModel.setZenmoneyPushInterceptEnabled(it) }
                )
            } else if (showBankSyncScreen) {
                BankSyncScreen(
                    state = state,
                    onBack = { 
                        showBankSyncScreen = false 
                        if (returnToNotificationSettingsFromBankSync) {
                            showNotificationSettingsScreen = true
                            returnToNotificationSettingsFromBankSync = false
                        }
                    },
                    onConfirmNotification = { notif, accId, catId ->
                        viewModel.confirmPendingNotification(notif, accId, catId)
                    },
                    onDismissNotification = { notif ->
                        viewModel.dismissPendingNotification(notif)
                    },
                    onParseManualText = { text ->
                        viewModel.parseAndProcessManualText(text)
                    },
                    onTogglePushNotifications = { viewModel.setPushNotificationsEnabled(it) },
                    onSendTestPush = { viewModel.sendTestPushNotification() }
                )
            } else {
                when (currentTab) {
                    ManiManiNavTab.HOME -> HomeScreen(
                        state = state,
                        onAddTransactionClick = { showAddTransactionDialog = true },
                        onAddAccountClick = { showAddAccountDialog = true },
                        onEditAccount = { accountToEdit = it },
                        onViewAllTransactions = { currentTab = ManiManiNavTab.HISTORY },
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        onEditTransaction = { transactionToEdit = it },
                        onCurrencyChange = { viewModel.setBaseCurrency(it) },
                        onOpenBankSync = { showBankSyncScreen = true },
                        onOpenNotificationSettings = { showNotificationSettingsScreen = true },
                        onConfirmNotification = { notif, accId, catId ->
                            viewModel.confirmPendingNotification(notif, accId, catId)
                        },
                        onDismissNotification = { notif ->
                            viewModel.dismissPendingNotification(notif)
                        },
                        onOpenGeminiAssistant = { promptType ->
                            if (promptType != null) {
                                viewModel.askGemini(promptType)
                            }
                            showGeminiAssistantScreen = true
                        },
                        onOpenPaydaySettings = { showPaydaySettingsDialog = true },
                        onOpenMe2MeTransfer = { showMe2MeTransferDialog = true },
                        onOpenIncomeDistribution = { showIncomeDistributionDialog = true },
                        onBankOfTheMonthSelect = { viewModel.setBankOfTheMonth(it) },
                        onUpdateAccountsOrder = { viewModel.updateAccountsOrder(it) }
                    )

                    ManiManiNavTab.HISTORY -> TransactionsScreen(
                        state = state,
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        onEditTransaction = { transactionToEdit = it }
                    )

                    ManiManiNavTab.ANALYTICS -> AnalyticsScreen(
                        state = state,
                        onOpenGeminiAssistant = { promptType ->
                            viewModel.askGemini(promptType)
                            showGeminiAssistantScreen = true
                        },
                        onAskAiQuestion = { userQuestion ->
                            viewModel.askGemini(AiPromptType.FULL_AUDIT, userQuestion)
                            showGeminiAssistantScreen = true
                        },
                        onNavigateToPlanning = { currentTab = ManiManiNavTab.PLANNING },
                        onOpenPaydaySettings = { showPaydaySettingsDialog = true }
                    )

                    ManiManiNavTab.PLANNING -> PlanningScreen(
                        state = state,
                        onAddBudget = { catId, limit -> viewModel.addBudget(catId, limit) },
                        onDeleteBudget = { viewModel.deleteBudget(it) },
                        onAddGoal = { name, target, curr, col, icon ->
                            viewModel.addGoal(name, target, curr, col, icon)
                        },
                        onEditGoal = { viewModel.updateGoal(it) },
                        onContributeGoal = { goalId, amount -> viewModel.contributeToGoal(goalId, amount) },
                        onDeleteGoal = { viewModel.deleteGoal(it) },
                        onAddDebt = { person, amt, isOwed, note -> viewModel.addDebt(person, amt, isOwed, note) },
                        onEditDebt = { viewModel.updateDebt(it) },
                        onToggleDebt = { viewModel.toggleDebtSettled(it) },
                        onDeleteDebt = { viewModel.deleteDebt(it) },
                        onAddPlannedTransaction = { viewModel.addPlannedTransaction(it) },
                        onUpdatePlannedTransaction = { viewModel.updatePlannedTransaction(it) },
                        onDeletePlannedTransaction = { viewModel.deletePlannedTransaction(it) },
                        onAddTransaction = { viewModel.addTransaction(
                            type = it.type,
                            amount = it.amount,
                            accountId = it.accountId,
                            categoryId = it.categoryId,
                            note = it.note,
                            excludeFromStats = it.excludeFromStats
                        ) },
                        onOpenGeminiAssistant = { promptType ->
                            viewModel.askGemini(promptType)
                            showGeminiAssistantScreen = true
                        },
                        onOpenPaydaySettings = { showPaydaySettingsDialog = true }
                    )

                    ManiManiNavTab.ACCOUNTS -> AccountsSettingsScreen(
                        viewModel = viewModel,
                        state = state,
                        onAddAccountClick = { showAddAccountDialog = true },
                        onManageCategories = { showManageCategoriesDialog = true },
                        onEditAccount = { accountToEdit = it },
                        onArchiveAccount = { viewModel.archiveAccount(it) },
                        onDeleteAccount = { viewModel.deleteAccount(it) },
                        onCurrencyChange = { viewModel.setBaseCurrency(it) },
                        onClearAllData = { keepAccounts -> viewModel.clearAllData(keepAccounts) },
                        onTogglePushNotifications = { viewModel.setPushNotificationsEnabled(it) },
                        onSendTestPush = { viewModel.sendTestPushNotification() },
                        onOpenBankSync = { showBankSyncScreen = true },
                        onOpenGeminiAssistant = { showGeminiAssistantScreen = true },
                        onSaveGeminiApiKey = { viewModel.saveGeminiApiKey(it) },
                        onTestGeminiApiKey = { key, callback -> viewModel.testGeminiApiKey(key, callback) },
                        onClearGeminiApiKey = { viewModel.clearGeminiApiKey() },
                        onOpenPaydaySettings = { showPaydaySettingsDialog = true },
                        onOpenMe2MeTransfer = { showMe2MeTransferDialog = true },
                        onOpenIncomeDistribution = { showIncomeDistributionDialog = true },
                        onBankOfTheMonthSelect = { viewModel.setBankOfTheMonth(it) },
                        onApplyUserBankStructure = { viewModel.applyUserBankStructure() },
                        onThemeModeChange = { viewModel.setThemeMode(it) }
                    )
                }
            }
        }
    }

    // Add Transaction Dialog
    if (showAddTransactionDialog) {
        AddTransactionDialog(
            accounts = state.accounts,
            categories = state.categories,
            goals = state.goals,
            debts = state.debts,
            bankOfTheMonth = state.bankOfTheMonth,
            onDismiss = { showAddTransactionDialog = false },
            onManageCategories = { showManageCategoriesDialog = true },
            onConfirm = { type, amount, accId, toAccId, catId, note, tag, exclude, goalId, debtId ->
                viewModel.addTransaction(
                    type = type,
                    amount = amount,
                    accountId = accId,
                    toAccountId = toAccId,
                    categoryId = catId,
                    note = note,
                    tag = tag,
                    excludeFromStats = exclude,
                    goalId = goalId,
                    debtId = debtId
                )
            }
        )
    }

    // Edit Transaction Dialog
    transactionToEdit?.let { txToEdit ->
        AddTransactionDialog(
            accounts = state.accounts,
            categories = state.categories,
            goals = state.goals,
            debts = state.debts,
            bankOfTheMonth = state.bankOfTheMonth,
            transactionToEdit = txToEdit,
            onDismiss = { transactionToEdit = null },
            onManageCategories = { showManageCategoriesDialog = true },
            onConfirm = { type, amount, accId, toAccId, catId, note, tag, exclude, goalId, debtId ->
                val updated = txToEdit.copy(
                    type = type,
                    amount = amount,
                    accountId = accId,
                    toAccountId = toAccId,
                    categoryId = catId,
                    note = note,
                    tag = tag,
                    excludeFromStats = exclude,
                    goalId = goalId,
                    debtId = debtId
                )
                viewModel.updateTransaction(txToEdit, updated)
                transactionToEdit = null
            }
        )
    }

    // Payday Settings Dialog
    if (showPaydaySettingsDialog) {
        PaydaySettingsDialog(
            currentPayday = state.payday,
            onDismiss = { showPaydaySettingsDialog = false },
            onConfirm = { day ->
                viewModel.setPayday(day)
                showPaydaySettingsDialog = false
            }
        )
    }

    // Me2Me Internal Transfer Dialog
    if (showMe2MeTransferDialog) {
        Me2MeTransferDialog(
            accounts = state.accounts,
            onDismiss = { showMe2MeTransferDialog = false },
            onConfirm = { fromId, toId, amount, note ->
                viewModel.executeMe2MeTransfer(fromId, toId, amount, note)
                showMe2MeTransferDialog = false
            }
        )
    }

    // Income Distribution Hub Dialog (e.g. cash deposit / wife's salary)

    if (showManageCategoriesDialog) {
        com.example.ui.components.ManageCategoriesDialog(
            categories = state.categories,
            onDismiss = { showManageCategoriesDialog = false },
            onAddCategory = { name, type, iconName, colorHex ->
                viewModel.addCategory(name, type, iconName, colorHex)
            },
            onUpdateCategory = { viewModel.updateCategory(it) },
            onDeleteCategory = { viewModel.deleteCategory(it) }
        )
    }
    if (showIncomeDistributionDialog) {
        IncomeDistributionDialog(
            accounts = state.accounts,
            categories = state.categories,
            goals = state.goals,
            debts = state.debts,
            onDismiss = { showIncomeDistributionDialog = false },
            onAddCategory = { name, type, iconName, colorHex ->
                viewModel.addCategory(name, type, iconName, colorHex)
            },
            onUpdateCategory = { viewModel.updateCategory(it) },
            onDeleteCategory = { viewModel.deleteCategory(it) },
            onConfirm = { sourceAccountId, allocations ->
                viewModel.executeIncomeDistribution(sourceAccountId, allocations)
                showIncomeDistributionDialog = false
            }
        )
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        AddEditAccountDialog(
            initialAccount = null,
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, type, balance, currency, colorHex, iconName, includeInTotal, includeInAnalytics ->
                viewModel.addAccount(name, type, balance, currency, colorHex, iconName, includeInTotal, includeInAnalytics)
            }
        )
    }

    // Edit Account Dialog
    accountToEdit?.let { acc ->
        AddEditAccountDialog(
            initialAccount = acc,
            onDismiss = { accountToEdit = null },
            onSave = { name, type, balance, currency, colorHex, iconName, includeInTotal, includeInAnalytics ->
                viewModel.updateAccount(
                    acc.copy(
                        name = name,
                        type = type,
                        balance = balance,
                        currency = currency,
                        colorHex = colorHex,
                        iconName = iconName,
                        includeInTotal = includeInTotal,
                        includeInAnalytics = includeInAnalytics
                    )
                )
                accountToEdit = null
            }
        )
    }
}
