with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

execute_dialog_code = """

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExecutePlanDialog(
    item: com.example.data.entity.PlannedTransactionEntity,
    accounts: List<com.example.data.entity.AccountEntity>,
    categories: List<com.example.data.entity.CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (accountId: Long, categoryId: Long?) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: -1L) }
    var selectedCategoryId by remember { mutableStateOf(item.categoryId) }
    
    var showAccountDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    
    val selectedAccountName = accounts.find { it.id == selectedAccountId }?.name ?: "Выберите счет"
    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Без категории"
    val isIncome = item.type == "INCOME"

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("execute_plan_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Исполнить операцию",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "${item.note} (${item.amount})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Account Selection
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = showAccountDropdown,
                    onExpandedChange = { showAccountDropdown = !showAccountDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedAccountName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Счет списания/зачисления") },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAccountDropdown) },
                        colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    androidx.compose.material3.ExposedDropdownMenu(
                        expanded = showAccountDropdown,
                        onDismissRequest = { showAccountDropdown = false }
                    ) {
                        accounts.forEach { acc ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedAccountId = acc.id
                                    showAccountDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Selection
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = !showCategoryDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категория") },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                        colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    androidx.compose.material3.ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        val filteredCategories = categories.filter { it.type == (if (isIncome) "INCOME" else "EXPENSE") }
                        filteredCategories.forEach { cat ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(selectedAccountId, selectedCategoryId)
                        },
                        enabled = selectedAccountId != -1L
                    ) {
                        Text("Исполнить")
                    }
                }
            }
        }
    }
}
"""

hook_code = """
    // Dialog: Execute Planned Payment
    if (itemToExecute != null) {
        ExecutePlanDialog(
            item = itemToExecute!!,
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = { itemToExecute = null },
            onConfirm = { accountId, categoryId ->
                onAddTransaction(
                    com.example.data.entity.TransactionEntity(
                        type = itemToExecute!!.type,
                        amount = itemToExecute!!.amount,
                        accountId = accountId,
                        categoryId = categoryId,
                        note = itemToExecute!!.note,
                        timestamp = System.currentTimeMillis()
                    )
                )
                onDeletePlannedTransaction(itemToExecute!!)
                itemToExecute = null
            }
        )
    }
"""

# Find place to inject ExecutePlanDialog usage
hook_target = "    // Dialog: Add Planned Payment"
content = content.replace(hook_target, hook_code + "\n" + hook_target)

# Add ExecutePlanDialog function at the end
content += execute_dialog_code

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
