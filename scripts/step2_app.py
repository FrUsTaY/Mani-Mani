with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    content = f.read()

old_str = """                        onDeleteDebt = { viewModel.deleteDebt(it) },
                        onOpenGeminiAssistant = { promptType ->"""

new_str = """                        onDeleteDebt = { viewModel.deleteDebt(it) },
                        onAddPlannedTransaction = { viewModel.addPlannedTransaction(it) },
                        onUpdatePlannedTransaction = { viewModel.updatePlannedTransaction(it) },
                        onDeletePlannedTransaction = { viewModel.deletePlannedTransaction(it) },
                        onOpenGeminiAssistant = { promptType ->"""

if "onAddPlannedTransaction" not in content:
    content = content.replace(old_str, new_str)
    with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
        f.write(content)
    print("ManiManiApp.kt updated")
