with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

# 1. Update PlannedPaymentsSheet signature
old_sheet_sig = """fun PlannedPaymentsSheet(
    items: List<com.example.data.entity.PlannedTransactionEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit,
    onDeleteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit
)"""
new_sheet_sig = """fun PlannedPaymentsSheet(
    items: List<com.example.data.entity.PlannedTransactionEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit,
    onDeleteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit,
    onExecuteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit
)"""
content = content.replace(old_sheet_sig, new_sheet_sig)

# 2. Add Execute Button
old_row_end = """                            IconButton(onClick = { onDeleteItem(item) }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }"""
new_row_end = """                            IconButton(onClick = { onExecuteItem(item) }) {
                                Icon(Icons.Default.Check, contentDescription = "Исполнить", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteItem(item) }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }"""
content = content.replace(old_row_end, new_row_end)

# 3. Add to the caller in ZenPlansMainView.kt
old_caller = """            onDeleteItem = { item ->
                onDeletePlannedTransaction(item)
            }
        )
    }"""
new_caller = """            onDeleteItem = { item ->
                onDeletePlannedTransaction(item)
            },
            onExecuteItem = { item ->
                itemToExecute = item
                showPlannedPaymentsSheet = false
            }
        )
    }"""
content = content.replace(old_caller, new_caller)

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
