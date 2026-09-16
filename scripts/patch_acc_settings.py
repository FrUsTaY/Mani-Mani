import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

# Replace showExportDialog with showBackupDialog
content = content.replace('var showExportDialog by remember { mutableStateOf(false) }', 'var showBackupDialog by remember { mutableStateOf(false) }')
content = content.replace('showExportDialog = true', 'showBackupDialog = true')
content = content.replace('showExportDialog = false', 'showBackupDialog = false')

# Replace the text of the button
content = content.replace('"Экспорт операций в CSV"', '"Экспорт/Импорт данных (JSON)"')

# Remove the CSV dialog and replace with Backup Dialog
backup_dialog_regex = re.compile(r'    // CSV Export Dialog.*?Dialog\(onDismissRequest = \{ showBackupDialog = false \}\) \{.*?Surface.*?Column.*?Row.*?Button.*?\}\n\s*\}\n\s*\}\n\s*\}\n\s*\}\n\s*\}\n', re.DOTALL)
# It's better to just manually inject the backup component code using string replacement on a known anchor

# Let's write a simple script to find the start of the CSV Export Dialog and replace until the next known section
