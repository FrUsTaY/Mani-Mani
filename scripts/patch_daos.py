import re

with open('app/src/main/java/com/example/data/dao/FinanceDaos.kt', 'r') as f:
    content = f.read()

# TransactionDao
if 'getAllTransactionsSync' not in content:
    content = content.replace(
        'fun getAllTransactions(): Flow<List<TransactionEntity>>',
        'fun getAllTransactions(): Flow<List<TransactionEntity>>\n\n    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")\n    suspend fun getAllTransactionsSync(): List<TransactionEntity>'
    )

# BudgetDao
if 'getAllBudgetsSync' not in content:
    content = content.replace(
        'fun getAllBudgets(): Flow<List<BudgetEntity>>',
        'fun getAllBudgets(): Flow<List<BudgetEntity>>\n\n    @Query("SELECT * FROM budgets")\n    suspend fun getAllBudgetsSync(): List<BudgetEntity>'
    )

# GoalDao
if 'getAllGoalsSync' not in content:
    content = content.replace(
        'fun getAllGoals(): Flow<List<GoalEntity>>',
        'fun getAllGoals(): Flow<List<GoalEntity>>\n\n    @Query("SELECT * FROM goals ORDER BY id ASC")\n    suspend fun getAllGoalsSync(): List<GoalEntity>'
    )

# DebtDao
if 'getAllDebtsSync' not in content:
    content = content.replace(
        'fun getAllDebts(): Flow<List<DebtEntity>>',
        'fun getAllDebts(): Flow<List<DebtEntity>>\n\n    @Query("SELECT * FROM debts ORDER BY isSettled ASC, id DESC")\n    suspend fun getAllDebtsSync(): List<DebtEntity>'
    )

# PlannedTransactionDao
if 'getAllPlannedTransactionsSync' not in content:
    content = content.replace(
        'fun getAllPlannedTransactions(): Flow<List<PlannedTransactionEntity>>',
        'fun getAllPlannedTransactions(): Flow<List<PlannedTransactionEntity>>\n\n    @Query("SELECT * FROM planned_transactions ORDER BY plannedDate ASC")\n    suspend fun getAllPlannedTransactionsSync(): List<PlannedTransactionEntity>'
    )
    
if 'getAllAccountsSync' not in content:
    content = content.replace(
        'fun getAllAccounts(): Flow<List<AccountEntity>>',
        'fun getAllAccounts(): Flow<List<AccountEntity>>\n\n    @Query("SELECT * FROM accounts ORDER BY isArchived ASC, orderIndex ASC, id ASC")\n    suspend fun getAllAccountsSync(): List<AccountEntity>'
    )

with open('app/src/main/java/com/example/data/dao/FinanceDaos.kt', 'w') as f:
    f.write(content)
