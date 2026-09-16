import re

with open('app/src/main/java/com/example/ui/components/ManageCategoriesDialog.kt', 'r') as f:
    content = f.read()

# Add an Add button in the header
old_header = r"""                Row\(
                    modifier = Modifier.fillMaxWidth\(\),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                \) \{
                    Text\("Управление категориями", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold\)
                    IconButton\(onClick = onDismiss\) \{ Icon\(Icons.Default.Close, "Закрыть"\) \}
                \}"""

new_header = """                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Управление категориями", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, "Добавить")
                    Spacer(Modifier.width(8.dp))
                    Text("Создать категорию")
                }"""

content = re.sub(old_header, new_header, content)

# Add showAddDialog state
state_block_old = r"""    var categoryToEdit by remember \{ mutableStateOf<CategoryEntity\?>\(null\) \}"""
state_block_new = """    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }"""

content = re.sub(state_block_old, state_block_new, content)

# Add onAddCategory parameter to ManageCategoriesDialog function signature
sig_old = r"""    onDeleteCategory: \(CategoryEntity\) -> Unit,
    onUpdateCategory: \(CategoryEntity\) -> Unit"""
sig_new = """    onDeleteCategory: (CategoryEntity) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onAddCategory: (name: String, type: String, iconName: String, colorHex: String) -> Unit"""

content = re.sub(sig_old, sig_new, content)

# Add the AddEditCategoryDialog for creating a new category
add_dialog = """
    if (showAddDialog) {
        AddEditCategoryDialog(
            categoryToEdit = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, type, iconName, colorHex ->
                onAddCategory(name, type, iconName, colorHex)
                showAddDialog = false
            }
        )
    }
"""

# inject right before the end
content = re.sub(r'    if \(categoryToEdit != null\) \{', add_dialog + '\n    if (categoryToEdit != null) {', content)

with open('app/src/main/java/com/example/ui/components/ManageCategoriesDialog.kt', 'w') as f:
    f.write(content)
