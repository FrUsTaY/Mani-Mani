import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'var showExportDialog by remember { mutableStateOf(false) }',
    'var showExportDialog by remember { mutableStateOf(false) }\n    var showApplyStructureDialog by remember { mutableStateOf(false) }'
)

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(content)
