import re

with open('app/src/main/java/com/example/data/repository/FinanceRepository.kt', 'r') as f:
    content = f.read()

# Update addTransaction
add_old = r"""    suspend fun addTransaction\(transaction: TransactionEntity\): Long \{
        val id = transactionDao\.insertTransaction\(transaction\)
        // Update account balances automatically
        when \(transaction\.type\) \{
            "EXPENSE" -> \{
                accountDao\.updateBalance\(transaction\.accountId, -transaction\.amount\)
            \}
            "INCOME" -> \{
                accountDao\.updateBalance\(transaction\.accountId, transaction\.amount\)
            \}
            "TRANSFER" -> \{
                accountDao\.updateBalance\(transaction\.accountId, -transaction\.amount\)
                transaction\.toAccountId\?\.let \{ toId ->
                    accountDao\.updateBalance\(toId, transaction\.amount\)
                \}
            \}
        \}
        return id
    \}"""
add_new = """    suspend fun addTransaction(transaction: TransactionEntity): Long {
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
    }"""
content = re.sub(add_old, add_new, content)

# Update deleteTransaction
delete_old = r"""    suspend fun deleteTransaction\(transaction: TransactionEntity\) \{
        // Reverse account balances
        when \(transaction\.type\) \{
            "EXPENSE" -> \{
                accountDao\.updateBalance\(transaction\.accountId, transaction\.amount\)
            \}
            "INCOME" -> \{
                accountDao\.updateBalance\(transaction\.accountId, -transaction\.amount\)
            \}
            "TRANSFER" -> \{
                accountDao\.updateBalance\(transaction\.accountId, transaction\.amount\)
                transaction\.toAccountId\?\.let \{ toId ->
                    accountDao\.updateBalance\(toId, -transaction\.amount\)
                \}
            \}
        \}
        transactionDao\.deleteTransaction\(transaction\)
    \}"""
delete_new = """    suspend fun deleteTransaction(transaction: TransactionEntity) {
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
    }"""
content = re.sub(delete_old, delete_new, content)

# Update updateTransaction
update_old = r"""    suspend fun updateTransaction\(
        oldTransaction: TransactionEntity,
        newTransaction: TransactionEntity
    \) \{
        // 1. Reverse previous transaction effect on account balances
        when \(oldTransaction\.type\) \{
            "EXPENSE" -> \{
                accountDao\.updateBalance\(oldTransaction\.accountId, oldTransaction\.amount\)
            \}
            "INCOME" -> \{
                accountDao\.updateBalance\(oldTransaction\.accountId, -oldTransaction\.amount\)
            \}
            "TRANSFER" -> \{
                accountDao\.updateBalance\(oldTransaction\.accountId, oldTransaction\.amount\)
                oldTransaction\.toAccountId\?\.let \{ toId ->
                    accountDao\.updateBalance\(toId, -oldTransaction\.amount\)
                \}
            \}
        \}
        // 2. Apply new transaction effect on account balances
        when \(newTransaction\.type\) \{
            "EXPENSE" -> \{
                accountDao\.updateBalance\(newTransaction\.accountId, -newTransaction\.amount\)
            \}
            "INCOME" -> \{
                accountDao\.updateBalance\(newTransaction\.accountId, newTransaction\.amount\)
            \}
            "TRANSFER" -> \{
                accountDao\.updateBalance\(newTransaction\.accountId, -newTransaction\.amount\)
                newTransaction\.toAccountId\?\.let \{ toId ->
                    accountDao\.updateBalance\(toId, newTransaction\.amount\)
                \}
            \}
        \}
        // 3. Update database record
        transactionDao\.updateTransaction\(newTransaction\)
    \}"""
update_new = """    suspend fun updateTransaction(
        oldTransaction: TransactionEntity,
        newTransaction: TransactionEntity
    ) {
        // Reverse old
        when (oldTransaction.type) {
            "EXPENSE" -> accountDao.updateBalance(oldTransaction.accountId, oldTransaction.amount)
            "INCOME" -> accountDao.updateBalance(oldTransaction.accountId, -oldTransaction.amount)
            "TRANSFER" -> {
                accountDao.updateBalance(oldTransaction.accountId, oldTransaction.amount)
                oldTransaction.toAccountId?.let { toId -> accountDao.updateBalance(toId, -oldTransaction.amount) }
            }
        }
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

        // Apply new
        when (newTransaction.type) {
            "EXPENSE" -> accountDao.updateBalance(newTransaction.accountId, -newTransaction.amount)
            "INCOME" -> accountDao.updateBalance(newTransaction.accountId, newTransaction.amount)
            "TRANSFER" -> {
                accountDao.updateBalance(newTransaction.accountId, -newTransaction.amount)
                newTransaction.toAccountId?.let { toId -> accountDao.updateBalance(toId, newTransaction.amount) }
            }
        }
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

        transactionDao.updateTransaction(newTransaction)
    }"""
content = re.sub(update_old, update_new, content)

with open('app/src/main/java/com/example/data/repository/FinanceRepository.kt', 'w') as f:
    f.write(content)
