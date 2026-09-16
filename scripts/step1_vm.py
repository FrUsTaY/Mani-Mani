import re

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

# 1. Add PlannedTransactionEntity to FinanceUiState
state_old = r"    val transactions: List<TransactionEntity> = emptyList\(\),"
state_new = "    val transactions: List<TransactionEntity> = emptyList(),\n    val plannedTransactions: List<PlannedTransactionEntity> = emptyList(),"
if "plannedTransactions: List<PlannedTransactionEntity>" not in content:
    content = re.sub(state_old, state_new, content)

# 2. Add combine10
combine9_block = r"fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> combine9\([\s\S]*?\) -> R\n\): Flow<R> = kotlinx\.coroutines\.flow\.combine\([\s\S]*?\) \{\s*args:\s*Array<\*>\s*->\n\s*transform\([\s\S]*?\)\n\}"
combine10_block = """fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> combine10(
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
"""
if "combine10" not in content:
    content = re.sub(combine9_block, combine9_block + "\n\n" + combine10_block, content)

# 3. Change baseFinanceFlow to combine10
base_flow_old = r"private val baseFinanceFlow = combine9\([\s\S]*?repository\.unprocessedNotifications,[\s\S]*?appConfigFlow,[\s\S]*?_statusMessage\n\s*\) \{ accounts, categories, transactions, budgets, goals, debts, pendingNotifications, config, status ->"
base_flow_new = """private val baseFinanceFlow = combine10(
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
    ) { accounts, categories, transactions, plannedTransactions, budgets, goals, debts, pendingNotifications, config, status ->"""
content = re.sub(base_flow_old, base_flow_new, content)

# 4. Add plannedTransactions to returned state in baseFinanceFlow
state_ret_old = r"            transactions = transactions,\n            budgets = budgets,"
state_ret_new = "            transactions = transactions,\n            plannedTransactions = plannedTransactions,\n            budgets = budgets,"
if "plannedTransactions = plannedTransactions" not in content:
    content = re.sub(state_ret_old, state_ret_new, content)

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
print("Done vm")
