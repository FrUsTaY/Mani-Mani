package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class FinanceRepository(private val db: AppDatabase) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val goalDao = db.goalDao()
    private val debtDao = db.debtDao()
    private val pendingNotificationDao = db.pendingNotificationDao()
    private val plannedTransactionDao = db.plannedTransactionDao()
    private val aiMessageDao = db.aiMessageDao()
    private val receiptDao = db.receiptDao()

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

    fun getPeriodSummary(startTime: Long?, endTime: Long?): Flow<com.example.data.dao.PeriodSummaryResult> {
        return if (startTime != null && endTime != null) {
            transactionDao.getPeriodSummary(startTime, endTime)
        } else {
            transactionDao.getAllTimeSummary()
        }
    }

    fun getHistoryTransactions(startTime: Long?, endTime: Long?, limit: Int): Flow<List<TransactionEntity>> {
        return if (startTime != null && endTime != null) {
            transactionDao.getTransactionsBetweenPaged(startTime, endTime, limit)
        } else {
            transactionDao.getAllTransactionsPaged(limit)
        }
    }

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
        
        val receipt = receiptDao.getReceiptEntityByTransactionId(transaction.id)
        receipt?.imagePath?.let { path ->
            try {
                java.io.File(path).delete()
            } catch (e: Exception) {}
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

        // 3. Reverse previous goal / debt effect
        oldTransaction.goalId?.let { goalId ->
            val goal = goalDao.getGoalById(goalId)
            if (goal != null) {
                val sign = if (oldTransaction.type == "INCOME") -1 else 1
                goalDao.updateGoal(goal.copy(currentAmount = goal.currentAmount - (oldTransaction.amount * sign)))
            }
        }
        oldTransaction.debtId?.let { debtId ->
            val debt = debtDao.getDebtById(debtId)
            if (debt != null) {
                val newAmount = debt.amount + oldTransaction.amount
                debtDao.updateDebt(debt.copy(amount = newAmount, isSettled = false))
            }
        }

        // 4. Apply new goal / debt effect
        newTransaction.goalId?.let { goalId ->
            val goal = goalDao.getGoalById(goalId)
            if (goal != null) {
                val sign = if (newTransaction.type == "INCOME") -1 else 1
                goalDao.updateGoal(goal.copy(currentAmount = goal.currentAmount + (newTransaction.amount * sign)))
            }
        }
        newTransaction.debtId?.let { debtId ->
            val debt = debtDao.getDebtById(debtId)
            if (debt != null) {
                val newAmount = (debt.amount - newTransaction.amount).coerceAtLeast(0.0)
                val isSettled = newAmount <= 0.0
                debtDao.updateDebt(debt.copy(amount = newAmount, isSettled = isSettled))
            }
        }

        // 5. Update database record
        transactionDao.updateTransaction(newTransaction)
    }

    // Budgets
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    suspend fun insertBudget(budget: BudgetEntity) = budgetDao.insertBudget(budget)
    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.deleteBudget(budget)

    // Goals
    val allGoals: Flow<List<GoalEntity>> = goalDao.getAllGoals()
    suspend fun getGoalById(id: Long): GoalEntity? = goalDao.getGoalById(id)
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
        aiMessageDao.deleteAllMessages()
        
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

    // AI Chat Messages
    val allAiMessages: Flow<List<com.example.service.gemini.AiMessage>> = aiMessageDao.getAllMessages().map { entities ->
        entities.map { entity ->
            com.example.service.gemini.AiMessage(
                id = entity.id,
                sender = if (entity.sender == "USER") com.example.service.gemini.MessageSender.USER else com.example.service.gemini.MessageSender.ASSISTANT,
                text = entity.text,
                timestamp = entity.timestamp,
                promptType = entity.promptType?.let {
                    try {
                        com.example.service.gemini.AiPromptType.valueOf(it)
                    } catch (e: Exception) {
                        null
                    }
                }
            )
        }
    }

    suspend fun saveAiMessage(message: com.example.service.gemini.AiMessage) {
        aiMessageDao.insertMessage(
            AiMessageEntity(
                id = message.id,
                sender = message.sender.name,
                text = message.text,
                timestamp = message.timestamp,
                promptType = message.promptType?.name
            )
        )
    }

    suspend fun clearAiMessages() {
        aiMessageDao.deleteAllMessages()
    }

    // Receipts
    fun getReceiptByTransactionId(transactionId: Long): Flow<ReceiptWithItems?> =
        receiptDao.getReceiptByTransactionId(transactionId)

    suspend fun getReceiptByTransactionIdSync(transactionId: Long): ReceiptWithItems? =
        receiptDao.getReceiptByTransactionIdSync(transactionId)

    suspend fun getReceiptEntityByTransactionId(transactionId: Long): ReceiptEntity? =
        receiptDao.getReceiptEntityByTransactionId(transactionId)

    suspend fun insertReceipt(receipt: ReceiptEntity): Long =
        receiptDao.insertReceipt(receipt)

    suspend fun updateReceipt(receipt: ReceiptEntity) =
        receiptDao.updateReceipt(receipt)

    suspend fun saveReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Long =
        receiptDao.saveReceiptWithItems(receipt, items)

    suspend fun deleteReceiptByTransactionId(transactionId: Long) {
        val existing = receiptDao.getReceiptEntityByTransactionId(transactionId)
        existing?.imagePath?.let { path ->
            try { java.io.File(path).delete() } catch (e: Exception) {}
        }
        receiptDao.deleteReceiptByTransactionId(transactionId)
    }

    suspend fun deleteReceipt(receipt: ReceiptEntity) {
        receipt.imagePath?.let { path ->
            try { java.io.File(path).delete() } catch (e: Exception) {}
        }
        receiptDao.deleteReceipt(receipt)
    }

    suspend fun deleteReceiptPhoto(transactionId: Long): String? {
        val existing = receiptDao.getReceiptByTransactionIdSync(transactionId) ?: return null
        val oldPath = existing.receipt.imagePath
        val hasQrData = !existing.receipt.rawQrData.isNullOrBlank() ||
                !existing.receipt.merchant.isNullOrBlank() ||
                existing.receipt.total != null ||
                existing.items.isNotEmpty()

        if (hasQrData) {
            receiptDao.updateReceipt(existing.receipt.copy(imagePath = null))
        } else {
            receiptDao.deleteReceipt(existing.receipt)
        }

        if (oldPath != null) {
            try { java.io.File(oldPath).delete() } catch (e: Exception) {}
        }
        return oldPath
    }

    suspend fun deleteReceiptQrData(transactionId: Long) {
        val existing = receiptDao.getReceiptByTransactionIdSync(transactionId) ?: return
        receiptDao.deleteReceiptItemsByReceiptId(existing.receipt.id)

        if (existing.receipt.imagePath != null) {
            receiptDao.updateReceipt(
                existing.receipt.copy(
                    merchant = null,
                    dateTime = null,
                    total = null,
                    fiscalNumber = null,
                    fiscalDocument = null,
                    fiscalSign = null,
                    operationType = null,
                    rawQrData = null
                )
            )
        } else {
            receiptDao.deleteReceipt(existing.receipt)
        }
    }

    suspend fun cleanOrphanReceiptFiles(receiptFileManager: com.example.service.receipt.ReceiptFileManager): Int {
        val allReceipts = receiptDao.getAllReceiptsSync()
        val validPaths = allReceipts.mapNotNull { it.imagePath }.toSet()
        return receiptFileManager.cleanOrphanFiles(validPaths)
    }
}
