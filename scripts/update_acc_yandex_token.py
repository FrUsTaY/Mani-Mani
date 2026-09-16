import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

# Replace `var cloudToken by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }`
# with `var cloudToken by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(viewModel.userFinancePrefs.getYandexToken())) }`

content = content.replace(
    'var cloudToken by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }',
    'var cloudToken by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(viewModel.userFinancePrefs.getYandexToken())) }'
)

# Replace the click handlers to save the token:
# `viewModel.exportBackupCloud(cloudToken.text.trim())`
# ->
# `viewModel.userFinancePrefs.setYandexToken(cloudToken.text.trim()); viewModel.exportBackupCloud(cloudToken.text.trim())`

content = content.replace(
    'viewModel.exportBackupCloud(cloudToken.text.trim())',
    'viewModel.userFinancePrefs.setYandexToken(cloudToken.text.trim())\n                                viewModel.exportBackupCloud(cloudToken.text.trim())'
)

content = content.replace(
    'viewModel.importBackupCloud(cloudToken.text.trim())',
    'viewModel.userFinancePrefs.setYandexToken(cloudToken.text.trim())\n                                viewModel.importBackupCloud(cloudToken.text.trim())'
)

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(content)
