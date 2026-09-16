import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

sig_old = r"""    onAddAccountClick: \(\) -> Unit,
    onEditAccount: \(AccountEntity\) -> Unit,"""
sig_new = """    onAddAccountClick: () -> Unit,
    onManageCategories: () -> Unit = {},
    onEditAccount: (AccountEntity) -> Unit,"""
content = re.sub(sig_old, sig_new, content)

# Find where to add the menu item.
# It seems there's a section for "Синхронизация с банками (Пуши / Чек)" or similar.
# Let's insert a menu item for Manage Categories after "Применить структуру моих счетов" or somewhere logical.
# Let's add it right before "Синхронизация с банками"

menu_item = """                    // Manage Categories
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManageCategories() }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Управление категориями", fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
"""

# Let's inject this before "Синхронизация с банками"
content = content.replace('                    Row(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .clickable { onOpenBankSync() }',
                          menu_item + '\n                    Row(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .clickable { onOpenBankSync() }')

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(content)
