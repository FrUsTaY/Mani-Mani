import re

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    content = f.read()

# Add Account Dialog
old_add = r"""    if \(showAddAccountDialog\) \{
        AddEditAccountDialog\(
            initialAccount = null,
            onDismiss = \{ showAddAccountDialog = false \},
            onSave = \{ name, type, balance, currency, colorHex, iconName ->
                viewModel\.addAccount\(name, type, balance, currency, colorHex, iconName\)
            \}
        \)
    \}"""

new_add = """    if (showAddAccountDialog) {
        AddEditAccountDialog(
            initialAccount = null,
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, type, balance, currency, colorHex, iconName, includeInTotal, includeInAnalytics ->
                viewModel.addAccount(name, type, balance, currency, colorHex, iconName, includeInTotal, includeInAnalytics)
            }
        )
    }"""
content = re.sub(old_add, new_add, content)

# Edit Account Dialog
old_edit = r"""    accountToEdit\?\.let \{ acc ->
        AddEditAccountDialog\(
            initialAccount = acc,
            onDismiss = \{ accountToEdit = null \},
            onSave = \{ name, type, balance, currency, colorHex, iconName ->
                viewModel\.updateAccount\(
                    acc\.copy\(
                        name = name,
                        type = type,
                        balance = balance,
                        currency = currency,
                        colorHex = colorHex,
                        iconName = iconName
                    \)
                \)
                accountToEdit = null
            \}
        \)
    \}"""

new_edit = """    accountToEdit?.let { acc ->
        AddEditAccountDialog(
            initialAccount = acc,
            onDismiss = { accountToEdit = null },
            onSave = { name, type, balance, currency, colorHex, iconName, includeInTotal, includeInAnalytics ->
                viewModel.updateAccount(
                    acc.copy(
                        name = name,
                        type = type,
                        balance = balance,
                        currency = currency,
                        colorHex = colorHex,
                        iconName = iconName,
                        includeInTotal = includeInTotal,
                        includeInAnalytics = includeInAnalytics
                    )
                )
                accountToEdit = null
            }
        )
    }"""
content = re.sub(old_edit, new_edit, content)

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
    f.write(content)
print("Done")
