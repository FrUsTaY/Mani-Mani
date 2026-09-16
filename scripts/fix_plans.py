with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

import re

old_logic = """    // "Ещё в планах" (Remaining planned expenses)
    val remainingPlannedExpenses = if (totalCategoryBudgets > 0) {
        (totalCategoryBudgets - spentSoFar).coerceAtLeast(0.0) + remainingPlannedPayments
    } else {
        remainingPlannedPayments
    }

    // "Деньги на месяц" (Total funds available this cycle)
    val baselineStartingBalance = (state.totalBalance - incomeSoFar).coerceAtLeast(0.0)
    val totalMoneyForMonth = baselineStartingBalance + incomeSoFar + remainingPlannedIncome

    // "Свободно на конец месяца" (Free money at end of month)
    val totalProjectedExpenses = spentSoFar + remainingPlannedExpenses
    val freeMoneyAtEndOfMonth = (totalMoneyForMonth - totalProjectedExpenses).coerceAtLeast(0.0)"""

new_logic = """    // "Ещё в планах" (Remaining planned expenses)
    val remainingCategoryBudgets = state.budgets.sumOf { budget ->
        val spentInCat = spendingByCategory[budget.categoryId] ?: 0.0
        (budget.limitAmount - spentInCat).coerceAtLeast(0.0)
    }
    val remainingPlannedExpenses = remainingCategoryBudgets + remainingPlannedPayments

    // "Деньги на месяц" (Total funds available this cycle)
    // Calculate the true baseline at the start of the period by adding back what was spent, and removing what was earned
    val baselineStartingBalance = (state.totalBalance + spentSoFar - incomeSoFar).coerceAtLeast(0.0)
    val totalMoneyForMonth = baselineStartingBalance + incomeSoFar + remainingPlannedIncome

    // "Свободно на конец месяца" (Free money at end of month)
    val totalProjectedExpenses = spentSoFar + remainingPlannedExpenses
    // Free money is current balance + future income - future remaining expenses
    val freeMoneyAtEndOfMonth = (state.totalBalance + remainingPlannedIncome - remainingPlannedExpenses).coerceAtLeast(0.0)"""

content = content.replace(old_logic, new_logic)

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
