with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    content = f.read()

caller_old = """                        onToggleDebt = { viewModel.toggleDebtSettled(it) },
                        onDeleteDebt = { viewModel.deleteDebt(it) },
                        onAddPlannedTransaction = { viewModel.addPlannedTransaction(it) },
                        onUpdatePlannedTransaction = { viewModel.updatePlannedTransaction(it) },
                        onDeletePlannedTransaction = { viewModel.deletePlannedTransaction(it) },
                        onOpenGeminiAssistant = { promptType ->
                            viewModel.askGemini(promptType)
                            showGeminiAssistantScreen = true
                        },"""
caller_new = """                        onToggleDebt = { viewModel.toggleDebtSettled(it) },
                        onDeleteDebt = { viewModel.deleteDebt(it) },
                        onAddPlannedTransaction = { viewModel.addPlannedTransaction(it) },
                        onUpdatePlannedTransaction = { viewModel.updatePlannedTransaction(it) },
                        onDeletePlannedTransaction = { viewModel.deletePlannedTransaction(it) },
                        onAddTransaction = { viewModel.addTransaction(
                            type = it.type,
                            amount = it.amount,
                            accountId = it.accountId,
                            categoryId = it.categoryId,
                            note = it.note,
                            excludeFromStats = it.excludeFromStats
                        ) },
                        onOpenGeminiAssistant = { promptType ->
                            viewModel.askGemini(promptType)
                            showGeminiAssistantScreen = true
                        },"""

content = content.replace(caller_old, caller_new)

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
    f.write(content)
