with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

# Replace AddPlannedPaymentDialog signature
old_sig = """fun AddPlannedPaymentDialog(
    existingItem: com.example.data.entity.PlannedTransactionEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, isIncome: Boolean, day: Int) -> Unit
)"""
new_sig = """@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddPlannedPaymentDialog(
    existingItem: com.example.data.entity.PlannedTransactionEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, isIncome: Boolean, day: Int, reminderType: String) -> Unit
)"""
content = content.replace(old_sig, new_sig.replace('@Composable\n@OptIn', '@OptIn'))
if '@OptIn' not in content[content.find('fun AddPlannedPaymentDialog') - 100:content.find('fun AddPlannedPaymentDialog')]:
    content = content.replace('@Composable\nfun AddPlannedPaymentDialog(', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun AddPlannedPaymentDialog(')

# Inside the dialog state
old_state = """    var dayText by remember { 
        mutableStateOf(
            existingItem?.plannedDate?.let { 
                Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH).toString() 
            } ?: "15"
        )
    }"""
new_state = """    var dayText by remember { 
        mutableStateOf(
            existingItem?.plannedDate?.let { 
                Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH).toString() 
            } ?: "15"
        )
    }
    var reminderType by remember { mutableStateOf(existingItem?.reminderType ?: "NONE") }
    var showReminderDropdown by remember { mutableStateOf(false) }
    
    val reminderLabels = mapOf(
        "NONE" to "Не напоминать",
        "ON_DAY" to "В день платежа (утром)",
        "1_DAY_BEFORE" to "За 1 день",
        "3_DAYS_BEFORE" to "За 3 дня"
    )"""
content = content.replace(old_state, new_state)

# After dayText OutlinedTextField, add Reminder dropdown
old_day_field = """                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("День месяца (1..31)") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )"""
new_day_field = """                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("День месяца (1..31)") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                ExposedDropdownMenuBox(
                    expanded = showReminderDropdown,
                    onExpandedChange = { showReminderDropdown = !showReminderDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = reminderLabels[reminderType] ?: "Не напоминать",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Напоминание") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showReminderDropdown) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showReminderDropdown,
                        onDismissRequest = { showReminderDropdown = false }
                    ) {
                        reminderLabels.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    reminderType = key
                                    showReminderDropdown = false
                                }
                            )
                        }
                    }
                }"""
content = content.replace(old_day_field, new_day_field)

# Replace onConfirm call
old_on_confirm_call = """                            onConfirm(title.trim(), amount, isIncome, day)"""
new_on_confirm_call = """                            onConfirm(title.trim(), amount, isIncome, day, reminderType)"""
content = content.replace(old_on_confirm_call, new_on_confirm_call)

# Update ZenPlansMainView caller
old_caller = """            onConfirm = { title, amount, isIncome, day ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                val newTimestamp = cal.timeInMillis
                if (itemToEdit != null) {
                    onUpdatePlannedTransaction(itemToEdit!!.copy(
                        note = title,
                        amount = amount,
                        type = if (isIncome) "INCOME" else "EXPENSE",
                        plannedDate = newTimestamp
                    ))
                } else {
                    onAddPlannedTransaction(
                        com.example.data.entity.PlannedTransactionEntity(
                            type = if (isIncome) "INCOME" else "EXPENSE",
                            amount = amount,
                            accountId = state.accounts.firstOrNull()?.id ?: 1L,
                            plannedDate = newTimestamp,
                            note = title
                        )
                    )
                }
                showAddPlannedPaymentDialog = false
                itemToEdit = null
            }"""
new_caller = """            onConfirm = { title, amount, isIncome, day, reminderType ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                val newTimestamp = cal.timeInMillis
                if (itemToEdit != null) {
                    onUpdatePlannedTransaction(itemToEdit!!.copy(
                        note = title,
                        amount = amount,
                        type = if (isIncome) "INCOME" else "EXPENSE",
                        plannedDate = newTimestamp,
                        reminderType = reminderType
                    ))
                } else {
                    onAddPlannedTransaction(
                        com.example.data.entity.PlannedTransactionEntity(
                            type = if (isIncome) "INCOME" else "EXPENSE",
                            amount = amount,
                            accountId = state.accounts.firstOrNull()?.id ?: 1L,
                            plannedDate = newTimestamp,
                            note = title,
                            reminderType = reminderType
                        )
                    )
                }
                showAddPlannedPaymentDialog = false
                itemToEdit = null
            }"""
content = content.replace(old_caller, new_caller)

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
