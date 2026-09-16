import re

with open('app/src/main/java/com/example/data/repository/FinanceRepository.kt', 'r') as f:
    repo = f.read()

# add dao
dao_old = "    private val pendingNotificationDao = db.pendingNotificationDao()"
dao_new = "    private val pendingNotificationDao = db.pendingNotificationDao()\n    private val plannedTransactionDao = db.plannedTransactionDao()"
if "plannedTransactionDao" not in repo:
    repo = repo.replace(dao_old, dao_new)

# add flows and methods
methods = """
    // Planned Transactions
    val allPlannedTransactions: Flow<List<PlannedTransactionEntity>> = plannedTransactionDao.getAllPlannedTransactions()

    suspend fun insertPlannedTransaction(transaction: PlannedTransactionEntity): Long = plannedTransactionDao.insertPlannedTransaction(transaction)
    suspend fun updatePlannedTransaction(transaction: PlannedTransactionEntity) = plannedTransactionDao.updatePlannedTransaction(transaction)
    suspend fun deletePlannedTransaction(transaction: PlannedTransactionEntity) = plannedTransactionDao.deletePlannedTransaction(transaction)
"""
if "allPlannedTransactions" not in repo:
    repo = repo + methods

with open('app/src/main/java/com/example/data/repository/FinanceRepository.kt', 'w') as f:
    f.write(repo)
print("Done repo")
