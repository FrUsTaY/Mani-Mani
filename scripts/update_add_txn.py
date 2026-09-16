import re

with open('app/src/main/java/com/example/ui/screens/add/AddTransactionDialog.kt', 'r') as f:
    content = f.read()

# 1. Signature
old_sig = r"""fun AddTransactionDialog\(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,"""
new_sig = """fun AddTransactionDialog(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    goals: List<GoalEntity> = emptyList(),
    debts: List<DebtEntity> = emptyList(),"""
content = re.sub(old_sig, new_sig, content)

old_cb = r"""    onConfirm: \(
        type: String,
        amount: Double,
        accountId: Long,
        toAccountId: Long\?,
        categoryId: Long\?,
        note: String,
        tag: String,
        excludeFromStats: Boolean
    \) -> Unit"""
new_cb = """    onConfirm: (
        type: String,
        amount: Double,
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        note: String,
        tag: String,
        excludeFromStats: Boolean,
        goalId: Long?,
        debtId: Long?
    ) -> Unit"""
content = re.sub(old_cb, new_cb, content)

# 2. Add state for selectedGoalId and selectedDebtId
state_block = r"""    var selectedToAccountId by remember \{
        mutableStateOf\(
            transactionToEdit\?\.toAccountId \?: activeAccounts\.getOrNull\(1\)\?\.id \?: activeAccounts\.firstOrNull\(\)\?\.id \?: 0L
        \)
    \}"""
new_state = """    var selectedToAccountId by remember {
        mutableStateOf(
            transactionToEdit?.toAccountId ?: activeAccounts.getOrNull(1)?.id ?: activeAccounts.firstOrNull()?.id ?: 0L
        )
    }

    var selectedGoalId by remember {
        mutableStateOf(transactionToEdit?.goalId)
    }
    var selectedDebtId by remember {
        mutableStateOf(transactionToEdit?.debtId)
    }"""
content = re.sub(state_block, new_state, content)

# 3. Import GoalEntity and DebtEntity
import_block = r"""import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity"""
new_import = """import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.GoalEntity
import com.example.data.entity.DebtEntity"""
content = re.sub(import_block, new_import, content)


# 4. Modify 'Куда' list (Transfers) to include goals
# The code is:
#                             items(accounts.filter { !it.isArchived && it.id != selectedAccountId }) { acc ->
#                                 val isSelected = acc.id == selectedToAccountId
#                                 val accColor = IconHelper.parseColor(acc.colorHex)
#                                 Surface(
#                                    ...
#                                 ) { ... }
#                             }

# We want to add an item list for goals.
# Right after that `items(...) { ... }` closing brace, before the `}` that closes `LazyRow`.
goals_render = """
                            items(goals) { goal ->
                                val isSelected = goal.id == selectedGoalId
                                val goalColor = IconHelper.parseColor(goal.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                                    modifier = Modifier.clickable { 
                                        selectedToAccountId = 0L 
                                        selectedGoalId = goal.id 
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(goalColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Копилка: " + goal.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = CurrencyHelper.formatAmount(goal.currentAmount, "RUB"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }"""

content = content.replace('items(accounts.filter { !it.isArchived && it.id != selectedAccountId }) { acc ->', 
                          '// accounts\n                            items(accounts.filter { !it.isArchived && it.id != selectedAccountId }) { acc ->')
# Modify acc clickable to clear goal
content = content.replace('modifier = Modifier.clickable { selectedToAccountId = acc.id }',
                          'modifier = Modifier.clickable { selectedToAccountId = acc.id; selectedGoalId = null }')

# 5. Insert goals after accounts
# Let's find the end of the accounts items block.
#                                         }
#                                     }
#                                 }
#                             }
#                         }
#                     }
#                 }
#                 if (selectedType == "TRANSFER") {
# We will insert it using regex.
target_lazy_row_end = r"""                                        \}
                                    \}
                                \}
                            \}
                        \}
                    \}
                \}
                if \(selectedType == "TRANSFER"\) \{"""

new_lazy_row_end = r"""                                        }
                                    }
                                }
                            }""" + goals_render + """
                        }
                    }
                }
                if (selectedType == "TRANSFER") {"""
content = re.sub(target_lazy_row_end, new_lazy_row_end, content)

# 6. Categories List (Debts)
debts_render = """
                            items(debts) { debt ->
                                val isSelected = debt.id == selectedDebtId
                                val debtColor = MaterialTheme.colorScheme.error
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) debtColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, debtColor) else null,
                                    modifier = Modifier
                                        .clickable { 
                                            selectedCategoryId = null 
                                            selectedDebtId = debt.id 
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(debtColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoneyOff,
                                                contentDescription = debt.personName,
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Долг: " + debt.personName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }"""

content = content.replace('modifier = Modifier.clickable { selectedCategoryId = cat.id }',
                          'modifier = Modifier.clickable { selectedCategoryId = cat.id; selectedDebtId = null }')
# Modify test tag since we replaced the clickable, wait:
content = content.replace('.clickable { selectedCategoryId = cat.id }\n                                        .testTag("category_chip_${cat.id}")',
                          '.clickable { selectedCategoryId = cat.id; selectedDebtId = null }\n                                        .testTag("category_chip_${cat.id}")')


# Find the end of the category items block
cat_items_end = r"""                                        \)
                                    \}
                                \}
                            \}
                            item \{"""

new_cat_items_end = r"""                                        )
                                    }
                                }
                            }""" + debts_render + """
                            item {"""
content = re.sub(cat_items_end, new_cat_items_end, content)

# 7. Add MoneyOff import
if "import androidx.compose.material.icons.filled.MoneyOff" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Settings",
                              "import androidx.compose.material.icons.filled.Settings\nimport androidx.compose.material.icons.filled.MoneyOff")

# 8. Update onConfirm call
confirm_old = r"""                            onConfirm\(
                                selectedType,
                                amount,
                                selectedAccountId,
                                if \(selectedType == "TRANSFER"\) selectedToAccountId else null,
                                if \(selectedType != "TRANSFER"\) selectedCategoryId else null,
                                noteText\.trim\(\),
                                tagText\.trim\(\),
                                excludeFromStats
                            \)"""
confirm_new = """                            // If transferring to a goal, toAccountId is null.
                            val finalToAccountId = if (selectedType == "TRANSFER" && selectedGoalId == null) selectedToAccountId else null
                            // If it's a debt, categoryId is null.
                            val finalCategoryId = if (selectedType != "TRANSFER" && selectedDebtId == null) selectedCategoryId else null
                            
                            onConfirm(
                                selectedType,
                                amount,
                                selectedAccountId,
                                finalToAccountId,
                                finalCategoryId,
                                noteText.trim(),
                                tagText.trim(),
                                excludeFromStats,
                                if (selectedType == "TRANSFER") selectedGoalId else null,
                                if (selectedType != "TRANSFER") selectedDebtId else null
                            )"""
content = re.sub(confirm_old, confirm_new, content)

# Fix validation
val_old = r"""                            if \(selectedType == "TRANSFER" && selectedAccountId == selectedToAccountId\) \{
                                errorMessage = "Выберите разные счета для перевода"
                                return@Button
                            \}"""
val_new = """                            if (selectedType == "TRANSFER" && selectedGoalId == null && selectedAccountId == selectedToAccountId) {
                                errorMessage = "Выберите разные счета для перевода"
                                return@Button
                            }
                            if (selectedType == "TRANSFER" && selectedGoalId == null && (selectedToAccountId == null || selectedToAccountId == 0L)) {
                                errorMessage = "Выберите счет зачисления или копилку"
                                return@Button
                            }
                            if (selectedType != "TRANSFER" && selectedCategoryId == null && selectedDebtId == null) {
                                errorMessage = "Выберите категорию или долг"
                                return@Button
                            }"""
content = re.sub(val_old, val_new, content)


with open('app/src/main/java/com/example/ui/screens/add/AddTransactionDialog.kt', 'w') as f:
    f.write(content)

print("Done")
