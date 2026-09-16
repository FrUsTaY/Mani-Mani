with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

import re

# Update ZenPlannedPaymentsDialog signature and usage
sheet_sig_old = """fun ZenPlannedPaymentsDialog(
    items: List<PlannedPaymentItem>,
    currency: String,
    onDismiss: () -> Unit,
    onAddItem: () -> Unit,
    onDeleteItem: (String) -> Unit
)"""
sheet_sig_new = """fun ZenPlannedPaymentsDialog(
    items: List<com.example.data.entity.PlannedTransactionEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit,
    onDeleteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit
)"""
content = content.replace(sheet_sig_old, sheet_sig_new)

# Update item in the list
item_row_old = """                            items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Colored date dot
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (item.isIncome) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${item.dayOfMonth}",
                                            color = if (item.isIncome) IncomeGreen else ExpenseRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (item.isIncome) "Доход" else "Расход",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = (if (item.isIncome) "+" else "-") + CurrencyHelper.formatAmount(item.amount, currency),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isIncome) IncomeGreen else MaterialTheme.colorScheme.onSurface
                                    )

                                    IconButton(onClick = { onDeleteItem(item.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))
                            }"""

item_row_new = """                            items.forEach { item ->
                                val cal = Calendar.getInstance().apply { timeInMillis = item.plannedDate }
                                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                                val isIncome = item.type == "INCOME"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEditItem(item) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Colored date dot
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isIncome) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${dayOfMonth}",
                                            color = if (isIncome) IncomeGreen else ExpenseRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.note,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isIncome) "Доход" else "Расход",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = (if (isIncome) "+" else "-") + CurrencyHelper.formatAmount(item.amount, currency),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.onSurface
                                    )

                                    IconButton(onClick = { onDeleteItem(item) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))
                            }"""
content = content.replace(item_row_old, item_row_new)


# Update AddPlannedPaymentDialog signature and logic
add_sig_old = """fun AddPlannedPaymentDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, isIncome: Boolean, day: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var dayText by remember { mutableStateOf("15") }"""

add_sig_new = """fun AddPlannedPaymentDialog(
    existingItem: com.example.data.entity.PlannedTransactionEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, isIncome: Boolean, day: Int) -> Unit
) {
    var title by remember { mutableStateOf(existingItem?.note ?: "") }
    var amountText by remember { mutableStateOf(existingItem?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var isIncome by remember { mutableStateOf(existingItem?.type == "INCOME") }
    var dayText by remember { 
        mutableStateOf(
            existingItem?.plannedDate?.let { 
                Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH).toString() 
            } ?: "15"
        )
    }"""
content = content.replace(add_sig_old, add_sig_new)

# Update onConfirm call inside the AddPlannedPaymentDialog to match the save button
confirm_btn_old = """                            if (title.isBlank() || amount <= 0 || day !in 1..31) return@Button
                            onConfirm(title.trim(), amount, isIncome, day)"""

confirm_btn_new = """                            if (title.isBlank() || amount <= 0 || day !in 1..31) return@Button
                            onConfirm(title.trim(), amount, isIncome, day)"""
# wait, confirm button code already is fine, just needs valid params.
btn_old = """                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            val day = dayText.toIntOrNull() ?: 15
                            if (title.isBlank() || amount <= 0 || day !in 1..31) return@Button
                            onConfirm(title.trim(), amount, isIncome, day)
                        },"""
if btn_old in content:
    pass

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
print("Dialogs updated")
