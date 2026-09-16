import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

# Remove the 'onRestoreDemoData' parameter
content = re.sub(r'\s*onRestoreDemoData:\s*\(\)\s*->\s*Unit\s*=\s*\{\},', '', content)

# Find the "Восстановить демо-данные" button and remove it
button_regex = re.compile(r'\s*OutlinedButton\(\s*onClick\s*=\s*\{\s*onRestoreDemoData\(\)\s*\},.*?Text\("Восстановить демо-данные", fontWeight = FontWeight.SemiBold\)\s*\}\s*', re.DOTALL)
content = button_regex.sub('\n            ', content)

# Add showApplyStructureDialog
content = content.replace(
    'var showIncomeDistributionDialog by remember { mutableStateOf(false) }',
    'var showIncomeDistributionDialog by remember { mutableStateOf(false) }\n    var showApplyStructureDialog by remember { mutableStateOf(false) }'
)

# Replace the direct call to onApplyUserBankStructure with setting the dialog state
content = content.replace(
    'onClick = onApplyUserBankStructure,',
    'onClick = { showApplyStructureDialog = true },'
)

# Add the dialog UI
dialog_code = """
    // Apply Structure Confirmation Dialog
    if (showApplyStructureDialog) {
        AlertDialog(
            onDismissRequest = { showApplyStructureDialog = false },
            title = { Text("Применить структуру счетов?") },
            text = { Text("ВНИМАНИЕ! Эта операция очистит текущую базу данных и применит вашу персонализированную структуру счетов (ВТБ, Т-Банк, Озон, Альфа, Яндекс). Все текущие транзакции будут удалены. Продолжить?") },
            confirmButton = {
                Button(onClick = {
                    onApplyUserBankStructure()
                    showApplyStructureDialog = false
                }) {
                    Text("Да, применить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showApplyStructureDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
"""

# Inject before "    // Backup & Restore Dialogs"
content = content.replace('    if (showExportDialog) {', dialog_code + '\n    if (showExportDialog) {')

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(content)
