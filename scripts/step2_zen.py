import re

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

# 1. Signature
sig_old = """    onDeleteBudget: (BudgetEntity) -> Unit,
    selectedSubTab: Int = 0,"""

sig_new = """    onDeleteBudget: (BudgetEntity) -> Unit,
    onAddPlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onUpdatePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    selectedSubTab: Int = 0,"""
if "onAddPlannedTransaction:" not in content:
    content = content.replace(sig_old, sig_new)

# 2. State
planned_old = """    // Planned items state (stored in session or preferences)
    var plannedPayments by remember {
        mutableStateOf<List<PlannedPaymentItem>>(emptyList())
    }"""
content = content.replace(planned_old, "")

# 3. Filter planned items based on DB
filter_old = """    // Planned upcoming payments in this cycle
    val remainingPlannedPayments = plannedPayments
        .filter { !it.isIncome && (it.dayOfMonth >= period.dayOfCycle || !isCurrentCycle) }
        .sumOf { it.amount }

    val remainingPlannedIncome = plannedPayments
        .filter { it.isIncome && (it.dayOfMonth >= period.dayOfCycle || !isCurrentCycle) }
        .sumOf { it.amount }"""

filter_new = """    // Planned upcoming payments in this cycle
    val remainingPlannedPayments = state.plannedTransactions
        .filter { it.type == "EXPENSE" && (Calendar.getInstance().apply { timeInMillis = it.plannedDate }.get(Calendar.DAY_OF_MONTH) >= period.dayOfCycle || !isCurrentCycle) }
        .sumOf { it.amount }

    val remainingPlannedIncome = state.plannedTransactions
        .filter { it.type == "INCOME" && (Calendar.getInstance().apply { timeInMillis = it.plannedDate }.get(Calendar.DAY_OF_MONTH) >= period.dayOfCycle || !isCurrentCycle) }
        .sumOf { it.amount }"""
content = content.replace(filter_old, filter_new)

# 4. Count of planned payments in top bar
count_old = """                                    text = "${plannedPayments.size}","""
count_new = """                                    text = "${state.plannedTransactions.size}","""
content = content.replace(count_old, count_new)

# 5. Expandable button "Запланировать доход"
btn_old = '                                        Text("Запланировать доход")'
btn_new = '                                        Text("Добавить план / операцию")'
content = content.replace(btn_old, btn_new)

# 6. Dialog calls
dialogs_old = """    // Sheet: Planned Operations [📅 count]
    if (showPlannedPaymentsSheet) {
        ZenPlannedPaymentsDialog(
            items = plannedPayments,
            currency = state.baseCurrency,
            onDismiss = { showPlannedPaymentsSheet = false },
            onAddItem = {
                showPlannedPaymentsSheet = false
                showAddPlannedPaymentDialog = true
            },
            onDeleteItem = { itemId ->
                plannedPayments = plannedPayments.filter { it.id != itemId }
            }
        )
    }

    // Dialog: Add Planned Payment
    if (showAddPlannedPaymentDialog) {
        AddPlannedPaymentDialog(
            onDismiss = { showAddPlannedPaymentDialog = false },
            onConfirm = { title, amount, isIncome, day ->
                plannedPayments = plannedPayments + PlannedPaymentItem(
                    title = title,
                    amount = amount,
                    isIncome = isIncome,
                    dayOfMonth = day
                )
                showAddPlannedPaymentDialog = false
            }
        )
    }"""

# Wait, we need to pass edit item state too
edit_state_new = """    var itemToEdit by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }"""
if "itemToEdit" not in content:
    content = content.replace("    var showAddPlannedPaymentDialog by remember { mutableStateOf(false) }", "    var showAddPlannedPaymentDialog by remember { mutableStateOf(false) }\n" + edit_state_new)

dialogs_new = """    // Sheet: Planned Operations [📅 count]
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
            }
        )
    }

    // Dialog: Add Planned Payment
    if (showAddPlannedPaymentDialog) {
        AddPlannedPaymentDialog(
            existingItem = itemToEdit,
            onDismiss = { showAddPlannedPaymentDialog = false; itemToEdit = null },
            onConfirm = { title, amount, isIncome, day ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                val newTimestamp = cal.timeInMillis
                if (itemToEdit != null) {
                    onUpdatePlannedTransaction(itemToEdit!!.copy(
                        note = title,
                        amount = amount,
                        type = if (isIncome) "INCOME" else "EXPENSE",
                        plannedDate = newTimestamp
                    ))
                } else {
                    onAddPlannedTransaction(
                        com.example.data.entity.PlannedTransactionEntity(
                            type = if (isIncome) "INCOME" else "EXPENSE",
                            amount = amount,
                            accountId = state.accounts.firstOrNull()?.id ?: 1L,
                            plannedDate = newTimestamp,
                            note = title
                        )
                    )
                }
                showAddPlannedPaymentDialog = false
                itemToEdit = null
            }
        )
    }"""
content = content.replace(dialogs_old, dialogs_new)

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)

print("ZenPlansMainView updated")
