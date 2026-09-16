import re

with open('app/src/main/java/com/example/ui/components/TransactionItemCard.kt', 'r') as f:
    content = f.read()

# Update signature
old_sig = r"""    accountsMap: Map<Long, AccountEntity>,
    categoriesMap: Map<Long, CategoryEntity>,
    onDelete: \(TransactionEntity\) -> Unit,"""
new_sig = """    accountsMap: Map<Long, AccountEntity>,
    categoriesMap: Map<Long, CategoryEntity>,
    goalsMap: Map<Long, com.example.data.entity.GoalEntity> = emptyMap(),
    debtsMap: Map<Long, com.example.data.entity.DebtEntity> = emptyMap(),
    onDelete: (TransactionEntity) -> Unit,"""
content = re.sub(old_sig, new_sig, content)

# Update logic
old_logic = r"""    val account = accountsMap\[transaction\.accountId\]
    val toAccount = transaction\.toAccountId\?\.let \{ accountsMap\[it\] \}
    val category = transaction\.categoryId\?\.let \{ categoriesMap\[it\] \}
    val currency = account\?\.currency \?: "RUB"

    val \(icon, badgeColor, title, subtitle\) = when \(transaction\.type\) \{
        "EXPENSE" -> \{
            val iconVec = category\?\.let \{ IconHelper\.getIconByName\(it\.iconName\) \} \?: IconHelper\.getIconByName\("shopping_cart"\)
            val color = category\?\.let \{ IconHelper\.parseColor\(it\.colorHex\) \} \?: ExpenseRed
            val sub = buildString \{
                append\(account\?\.name \?: "Счёт"\)
                if \(transaction\.note\.isNotBlank\(\)\) append\(" • \$\{transaction\.note\}"\)
            \}
            Quad\(iconVec, color, category\?\.name \?: "Расход", sub\)
        \}
        "INCOME" -> \{
            val iconVec = category\?\.let \{ IconHelper\.getIconByName\(it\.iconName\) \} \?: IconHelper\.getIconByName\("payments"\)
            val color = category\?\.let \{ IconHelper\.parseColor\(it\.colorHex\) \} \?: IncomeGreen
            val sub = buildString \{
                append\(account\?\.name \?: "Счёт"\)
                if \(transaction\.note\.isNotBlank\(\)\) append\(" • \$\{transaction\.note\}"\)
            \}
            Quad\(iconVec, color, category\?\.name \?: "Доход", sub\)
        \}
        else -> \{
            val iconVec = Icons\.AutoMirrored\.Filled\.CompareArrows
            val color = TransferBlue
            val fromName = account\?\.name \?: "Счёт"
            val toName = toAccount\?\.name \?: "Счёт"
            Quad\(iconVec, color, "Перевод", "\$fromName → \$toName"\)
        \}
    \}"""

new_logic = """    val account = accountsMap[transaction.accountId]
    val toAccount = transaction.toAccountId?.let { accountsMap[it] }
    val category = transaction.categoryId?.let { categoriesMap[it] }
    val goal = transaction.goalId?.let { goalsMap[it] }
    val debt = transaction.debtId?.let { debtsMap[it] }
    val currency = account?.currency ?: "RUB"

    val (icon, badgeColor, title, subtitle) = when (transaction.type) {
        "EXPENSE" -> {
            val iconVec = if (debt != null) Icons.Default.MoneyOff else category?.let { IconHelper.getIconByName(it.iconName) } ?: IconHelper.getIconByName("shopping_cart")
            val color = if (debt != null) MaterialTheme.colorScheme.error else category?.let { IconHelper.parseColor(it.colorHex) } ?: ExpenseRed
            val sub = buildString {
                append(account?.name ?: "Счёт")
                if (transaction.note.isNotBlank()) append(" • ${transaction.note}")
            }
            val dispTitle = debt?.let { "Долг: ${it.personName}" } ?: category?.name ?: "Расход"
            Quad(iconVec, color, dispTitle, sub)
        }
        "INCOME" -> {
            val iconVec = if (debt != null) Icons.Default.MoneyOff else category?.let { IconHelper.getIconByName(it.iconName) } ?: IconHelper.getIconByName("payments")
            val color = if (debt != null) MaterialTheme.colorScheme.error else category?.let { IconHelper.parseColor(it.colorHex) } ?: IncomeGreen
            val sub = buildString {
                append(account?.name ?: "Счёт")
                if (transaction.note.isNotBlank()) append(" • ${transaction.note}")
            }
            val dispTitle = debt?.let { "Возврат долга: ${it.personName}" } ?: category?.name ?: "Доход"
            Quad(iconVec, color, dispTitle, sub)
        }
        else -> {
            val iconVec = if (goal != null) IconHelper.getIconByName(goal.iconName) else Icons.AutoMirrored.Filled.CompareArrows
            val color = if (goal != null) IconHelper.parseColor(goal.colorHex) else TransferBlue
            val fromName = account?.name ?: "Счёт"
            val toName = if (goal != null) "Копилка: ${goal.name}" else toAccount?.name ?: "Счёт"
            val dispTitle = if (goal != null) "В копилку" else "Перевод"
            Quad(iconVec, color, dispTitle, "$fromName → $toName")
        }
    }"""
content = re.sub(old_logic, new_logic, content)

# Add MoneyOff import
if "import androidx.compose.material.icons.filled.MoneyOff" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Delete",
                              "import androidx.compose.material.icons.filled.Delete\nimport androidx.compose.material.icons.filled.MoneyOff")

with open('app/src/main/java/com/example/ui/components/TransactionItemCard.kt', 'w') as f:
    f.write(content)
