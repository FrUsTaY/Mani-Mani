import re

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    content = f.read()

# Add state variable
state_old = "var showIncomeDistributionDialog by remember { mutableStateOf(false) }"
state_new = """var showIncomeDistributionDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }"""
content = content.replace(state_old, state_new)

# Add rendering of ManageCategoriesDialog
dialog_block = """
    if (showManageCategoriesDialog) {
        com.example.ui.components.ManageCategoriesDialog(
            categories = state.categories,
            onDismiss = { showManageCategoriesDialog = false },
            onAddCategory = { name, type, iconName, colorHex ->
                viewModel.addCategory(name, type, iconName, colorHex)
            },
            onUpdateCategory = { viewModel.updateCategory(it) },
            onDeleteCategory = { viewModel.deleteCategory(it) }
        )
    }
"""
content = content.replace("    if (showIncomeDistributionDialog) {", dialog_block + "    if (showIncomeDistributionDialog) {")

# Update AccountsSettingsScreen call
accounts_old = r"""AccountsSettingsScreen\(
                        state = state,
                        onAddAccountClick = \{ showAddAccountDialog = true \},"""
accounts_new = """AccountsSettingsScreen(
                        state = state,
                        onAddAccountClick = { showAddAccountDialog = true },
                        onManageCategories = { showManageCategoriesDialog = true },"""
content = re.sub(accounts_old, accounts_new, content)

# Update AddTransactionDialog calls (there are two: add and edit)
# Add
add_old = r"""AddTransactionDialog\(
            accounts = state.accounts,
            categories = state.categories,
            bankOfTheMonth = state.bankOfTheMonth,
            onDismiss = \{ showAddTransactionDialog = false \},"""
add_new = """AddTransactionDialog(
            accounts = state.accounts,
            categories = state.categories,
            bankOfTheMonth = state.bankOfTheMonth,
            onDismiss = { showAddTransactionDialog = false },
            onManageCategories = { showManageCategoriesDialog = true },"""
content = re.sub(add_old, add_new, content)

# Edit
edit_old = r"""AddTransactionDialog\(
            accounts = state.accounts,
            categories = state.categories,
            bankOfTheMonth = state.bankOfTheMonth,
            transactionToEdit = txToEdit,
            onDismiss = \{ transactionToEdit = null \},"""
edit_new = """AddTransactionDialog(
            accounts = state.accounts,
            categories = state.categories,
            bankOfTheMonth = state.bankOfTheMonth,
            transactionToEdit = txToEdit,
            onDismiss = { transactionToEdit = null },
            onManageCategories = { showManageCategoriesDialog = true },"""
content = re.sub(edit_old, edit_new, content)

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
    f.write(content)
