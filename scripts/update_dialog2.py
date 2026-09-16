import re

with open('app/src/main/java/com/example/ui/screens/accounts/AddEditAccountDialog.kt', 'r') as f:
    content = f.read()

# Update onSave call
old_onsave = "onSave(name.trim(), selectedType, bal, selectedCurrency, selectedColor, selectedIcon)"
new_onsave = "onSave(name.trim(), selectedType, bal, selectedCurrency, selectedColor, selectedIcon, includeInTotal, includeInAnalytics)"
content = content.replace(old_onsave, new_onsave)

# Insert switches before the error message or buttons
old_ui = """                Spacer(modifier = Modifier.height(16.dp))

                errorMessage?.let {"""

new_ui = """                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Включить в общий баланс", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Учитывать средства в общей сумме капитала",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeInTotal,
                        onCheckedChange = { includeInTotal = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Учитывать в аналитике", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "В доходах и расходах (отключите для копилок)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeInAnalytics,
                        onCheckedChange = { includeInAnalytics = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                errorMessage?.let {"""

content = content.replace(old_ui, new_ui)

with open('app/src/main/java/com/example/ui/screens/accounts/AddEditAccountDialog.kt', 'w') as f:
    f.write(content)
print("Done")
