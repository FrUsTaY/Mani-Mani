import re

with open('app/src/main/java/com/example/ui/components/IncomeDistributionDialog.kt', 'r') as f:
    content = f.read()

old_block = r"""    if \(showQuickAddCategory\) \{
        var newCatName by remember \{ mutableStateOf\(""\) \}
        AlertDialog\(
            onDismissRequest = \{ showQuickAddCategory = false \},
            title = \{ Text\("Новая категория"\) \},
            text = \{
                OutlinedTextField\(
                    value = newCatName,
                    onValueChange = \{ newCatName = it \},
                    label = \{ Text\("Название \(напр\. Ипотека\)"\) \},
                    singleLine = true
                \)
            \},
            confirmButton = \{
                TextButton\(
                    onClick = \{
                        if \(newCatName\.isNotBlank\(\)\) \{
                            onAddCategory\(newCatName\.trim\(\), "EXPENSE", "category_expense", "#FF5252"\) // Default icon/color
                            showQuickAddCategory = false
                            // Note: We don't automatically select it here because we don't have its new ID yet, 
                            // user will need to open the dropdown again to select it\.
                        \}
                    \},
                    enabled = newCatName\.isNotBlank\(\)
                \) \{ Text\("Добавить"\) \}
            \},
            dismissButton = \{
                TextButton\(onClick = \{ showQuickAddCategory = false \}\) \{ Text\("Отмена"\) \}
            \}
        \)
    \}"""

new_block = """    if (showQuickAddCategory) {
        AddEditCategoryDialog(
            categoryToEdit = null,
            defaultType = "EXPENSE",
            onDismiss = { showQuickAddCategory = false },
            onSave = { name, type, iconName, colorHex ->
                onAddCategory(name, type, iconName, colorHex)
                showQuickAddCategory = false
            }
        )
    }"""

new_content = re.sub(old_block, new_block, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/components/IncomeDistributionDialog.kt', 'w') as f:
    f.write(new_content)
