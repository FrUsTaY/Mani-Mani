import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'fun AccountsSettingsScreen(\n    state: FinanceUiState,',
    'fun AccountsSettingsScreen(\n    viewModel: com.example.ui.viewmodel.FinanceViewModel,\n    state: FinanceUiState,'
)

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    app_content = f.read()

app_content = app_content.replace(
    'AccountsSettingsScreen(\n                        state = state,',
    'AccountsSettingsScreen(\n                        viewModel = viewModel,\n                        state = state,'
)

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
    f.write(app_content)
