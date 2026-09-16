with open('app/src/main/java/com/example/ui/screens/planning/PlanningScreen.kt', 'r') as f:
    content = f.read()

sig_old = """    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},"""
sig_new = """    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onAddTransaction: (com.example.data.entity.TransactionEntity) -> Unit = {},"""
content = content.replace(sig_old, sig_new)

caller_old = """            onDeletePlannedTransaction = onDeletePlannedTransaction,
            onOpenPaydaySettings = onOpenPaydaySettings,
            onOpenGeminiAssistant = onOpenGeminiAssistant,"""
caller_new = """            onDeletePlannedTransaction = onDeletePlannedTransaction,
            onAddTransaction = onAddTransaction,
            onOpenPaydaySettings = onOpenPaydaySettings,
            onOpenGeminiAssistant = onOpenGeminiAssistant,"""
content = content.replace(caller_old, caller_new)

with open('app/src/main/java/com/example/ui/screens/planning/PlanningScreen.kt', 'w') as f:
    f.write(content)
