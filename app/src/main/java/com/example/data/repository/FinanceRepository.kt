package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class FinanceRepository(private val db: AppDatabase) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val goalDao = db.goalDao()
    private val debtDao = db.debtDao()
    private val pendingNotificationDao = db.pendingNotificationDao()
    private val plannedTransactionDao = db.plannedTransactionDao()

    // Pending Bank Notifications
    val unprocessedNotifications: Flow<List<PendingNotificationEntity>> = pendingNotificationDao.getUnprocessedNotifications()
    val allRecentNotifications: Flow<List<PendingNotificationEntity>> = pendingNotificationDao.getAllRecentNotifications()

    suspend fun insertPendingNotification(notification: PendingNotificationEntity): Long =
        pendingNotificationDao.insertNotification(notification)

    suspend fun markNotificationProcessed(id: Long) =
        pendingNotificationDao.markAsProcessed(id)

    suspend fun deletePendingNotification(notification: PendingNotificationEntity) =
        pendingNotificationDao.deleteNotification(notification)

    suspend fun clearProcessedNotifications() =
        pendingNotificationDao.clearProcessed()

    // Accounts
    val activeAccounts: Flow<List<AccountEntity>> = accountDao.getActiveAccounts()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    suspend fun getAccountById(id: Long) = accountDao.getAccountById(id)
    suspend fun insertAccount(account: AccountEntity) = accountDao.insertAccount(account)
    suspend fun updateAccount(account: AccountEntity) = accountDao.updateAccount(account)
    suspend fun updateAccounts(accounts: List<AccountEntity>) = accountDao.updateAccounts(accounts)
    suspend fun deleteAccount(account: AccountEntity) = accountDao.deleteAccount(account)

    // Categories
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>> = categoryDao.getCategoriesByType(type)
    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: CategoryEntity) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.deleteCategory(category)

    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    fun getRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>> = transactionDao.getRecentTransactions(limit)

    suspend fun addTransaction(transaction: TransactionEntity): Long {
        val id = transactionDao.insertTransaction(transaction)
        
        // Update account balances automatically
        when (transaction.type) {
            "EXPENSE" -> {
                accountDao.updateBalance(transaction.accountId, -transaction.amount)
            }
            "INCOME" -> {
                accountDao.updateBalance(transaction.accountId, transaction.amount)
            }
            "TRANSFER" -> {
                accountDao.updateBalance(transaction.accountId, -transaction.amount)
                transaction.toAccountId?.let { toId ->
                    accountDao.updateBalance(toId, transaction.amount)
                }
            }
        }
        
        // Handle Goal funding (typically a TRANSFER, but we check if goalId is present)
        transaction.goalId?.let { goalId ->
            val goal = goalDao.getGoalById(goalId)
            if (goal != null) {
                // If it's a transfer, we added to it. (Or expense)
                val sign = if (transaction.type == "INCOME") -1 else 1 
                goalDao.updateGoal(goal.copy(currentAmount = goal.currentAmount + (transaction.amount * sign)))
            }
        }
        
        // Handle Debt repayment
        transaction.debtId?.let { debtId ->
            val debt = debtDao.getDebtById(debtId)
            if (debt != null) {
                // If I'm paying a debt (EXPENSE), amount owed decreases
                // If I'm receiving a debt payment (INCOME), amount owed to me decreases
                // Basically, debt amount reduces by transaction.amount
                val newAmount = (debt.amount - transaction.amount).coerceAtLeast(0.0)
                val isSettled = newAmount <= 0.0
                debtDao.updateDebt(debt.copy(amount = newAmount, isSettled = isSettled))
            }
        }
        
        return id
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        // Reverse account balances
        when (transaction.type) {
            "EXPENSE" -> {
                accountDao.updateBalance(transaction.accountId, transaction.amount)
            }
            "INCOME" -> {
                accountDao.updateBalance(transaction.accountId, -transaction.amount)
            }
            "TRANSFER" -> {
                accountDao.updateBalance(transaction.accountId, transaction.amount)
                transaction.toAccountId?.let { toId ->
                    accountDao.updateBalance(toId, -transaction.amount)
                }
            }
        }
        
        transaction.goalId?.let { goalId ->
            val goal = goalDao.getGoalById(goalId)
            if (goal != null) {
                val sign = if (transaction.type == "INCOME") -1 else 1
                goalDao.updateGoal(goal.copy(currentAmount = goal.currentAmount - (transaction.amount * sign)))
            }
        }
        
        transaction.debtId?.let { debtId ->
            val debt = debtDao.getDebtById(debtId)
            if (debt != null) {
                val newAmount = debt.amount + transaction.amount
                debtDao.updateDebt(debt.copy(amount = newAmount, isSettled = false))
            }
        }
        
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun updateTransaction(
        oldTransaction: TransactionEntity,
        newTransaction: TransactionEntity
    ) {
        // 1. Reverse previous transaction effect on account balances
        when (oldTransaction.type) {
            "EXPENSE" -> {
                accountDao.updateBalance(oldTransaction.accountId, oldTransaction.amount)
            }
            "INCOME" -> {
                accountDao.updateBalance(oldTransaction.accountId, -oldTransaction.amount)
            }
            "TRANSFER" -> {
                accountDao.updateBalance(oldTransaction.accountId, oldTransaction.amount)
                oldTransaction.toAccountId?.let { toId ->
                    accountDao.updateBalance(toId, -oldTransaction.amount)
                }
            }
        }

        // 2. Apply new transaction effect on account balances
        when (newTransaction.type) {
            "EXPENSE" -> {
                accountDao.updateBalance(newTransaction.accountId, -newTransaction.amount)
            }
            "INCOME" -> {
                accountDao.updateBalance(newTransaction.accountId, newTransaction.amount)
            }
            "TRANSFER" -> {
                accountDao.updateBalance(newTransaction.accountId, -newTransaction.amount)
                newTransaction.toAccountId?.let { toId ->
                    accountDao.updateBalance(toId, newTransaction.amount)
                }
            }
        }

        // 3. Update database record
        transactionDao.updateTransaction(newTransaction)
    }

    // Budgets
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    suspend fun insertBudget(budget: BudgetEntity) = budgetDao.insertBudget(budget)
    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.deleteBudget(budget)

    // Goals
    val allGoals: Flow<List<GoalEntity>> = goalDao.getAllGoals()
    suspend fun insertGoal(goal: GoalEntity) = goalDao.insertGoal(goal)
    suspend fun updateGoal(goal: GoalEntity) = goalDao.updateGoal(goal)
    suspend fun deleteGoal(goal: GoalEntity) = goalDao.deleteGoal(goal)
    suspend fun contributeToGoal(goalId: Long, amount: Double) {
        val goals = allGoals.firstOrNull() ?: return
        val target = goals.find { it.id == goalId } ?: return
        goalDao.updateGoal(target.copy(currentAmount = target.currentAmount + amount))
    }

    // Debts
    val allDebts: Flow<List<DebtEntity>> = debtDao.getAllDebts()
    suspend fun insertDebt(debt: DebtEntity) = debtDao.insertDebt(debt)
    suspend fun updateDebt(debt: DebtEntity) = debtDao.updateDebt(debt)
    suspend fun deleteDebt(debt: DebtEntity) = debtDao.deleteDebt(debt)

    // Clear all data for real accounting
    suspend fun clearAllData(keepAccountStructure: Boolean = true) {
        transactionDao.deleteAllTransactions()
        debtDao.deleteAllDebts()
        pendingNotificationDao.deleteAllNotifications()
        
        // Reset goal current progress
        val goals = allGoals.firstOrNull() ?: emptyList()
        goals.forEach { goalDao.updateGoal(it.copy(currentAmount = 0.0)) }

        if (keepAccountStructure) {
            accountDao.resetAllAccountBalances()
        } else {
            accountDao.deleteAllAccounts()
        }
    }

    // Reset data to defaults (legacy/internal)
    suspend fun resetData() {
        AppDatabase.prepopulateDatabase(db)
    }

    // Planned Transactions
    val allPlannedTransactions: Flow<List<PlannedTransactionEntity>> = plannedTransactionDao.getAllPlannedTransactions()

    suspend fun insertPlannedTransaction(transaction: PlannedTransactionEntity): Long = plannedTransactionDao.insertPlannedTransaction(transaction)
    suspend fun updatePlannedTransaction(transaction: PlannedTransactionEntity) = plannedTransactionDao.updatePlannedTransaction(transaction)
    suspend fun deletePlannedTransaction(transaction: PlannedTransactionEntity) = plannedTransactionDao.deletePlannedTransaction(transaction)
}
