with open('app/src/main/java/com/example/ui/screens/planning/PlanningScreen.kt', 'r') as f:
    content = f.read()

import re

# Add to PlanningScreen parameters
sig_old = """    onDeleteDebt: (DebtEntity) -> Unit,
    onOpenGeminiAssistant: (AiPromptType) -> Unit = {},"""

sig_new = """    onDeleteDebt: (DebtEntity) -> Unit,
    onAddPlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onUpdatePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onOpenGeminiAssistant: (AiPromptType) -> Unit = {},"""

if "onAddPlannedTransaction:" not in content:
    content = content.replace(sig_old, sig_new)

# Pass to ZenPlansMainView
call_old = """                onDeleteBudget = onDeleteBudget,
                selectedSubTab = selectedTab,"""

call_new = """                onDeleteBudget = onDeleteBudget,
                onAddPlannedTransaction = onAddPlannedTransaction,
                onUpdatePlannedTransaction = onUpdatePlannedTransaction,
                onDeletePlannedTransaction = onDeletePlannedTransaction,
                selectedSubTab = selectedTab,"""

if "onAddPlannedTransaction = onAddPlannedTransaction" not in content:
    content = content.replace(call_old, call_new)

with open('app/src/main/java/com/example/ui/screens/planning/PlanningScreen.kt', 'w') as f:
    f.write(content)
print("PlanningScreen.kt updated")
