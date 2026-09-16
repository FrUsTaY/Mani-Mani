package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.CategoryEntity
import com.example.ui.theme.ExpenseRed
import com.example.ui.util.IconHelper

@Composable
fun ManageCategoriesDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onAddCategory: (name: String, type: String, iconName: String, colorHex: String) -> Unit
) {
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    val grouped = categories.groupBy { it.type }
                    
                    if (grouped.containsKey("INCOME")) {
                        item { Text("Доходы", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(grouped["INCOME"] ?: emptyList()) { cat ->
                            CategoryItemRow(cat, onEdit = { categoryToEdit = it }, onDelete = { onDeleteCategory(it) })
                        }
                    }
                    
                    if (grouped.containsKey("EXPENSE")) {
                        item { Text("Расходы", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(grouped["EXPENSE"] ?: emptyList()) { cat ->
                            CategoryItemRow(cat, onEdit = { categoryToEdit = it }, onDelete = { onDeleteCategory(it) })
                        }
                    }
                }
            }
        }
    }


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

    if (categoryToEdit != null) {
        AddEditCategoryDialog(
            categoryToEdit = categoryToEdit,
            onDismiss = { categoryToEdit = null },
            onSave = { name, type, iconName, colorHex ->
                onUpdateCategory(categoryToEdit!!.copy(name = name, type = type, iconName = iconName, colorHex = colorHex))
                categoryToEdit = null
            }
        )
    }
}

@Composable
fun CategoryItemRow(
    category: CategoryEntity,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onEdit(category) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(IconHelper.parseColor(category.colorHex).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = IconHelper.getIconByName(category.iconName),
                contentDescription = null,
                tint = IconHelper.parseColor(category.colorHex),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(category.name, modifier = Modifier.weight(1f))
        IconButton(onClick = { onEdit(category) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, "Изменить", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { onDelete(category) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Удалить", modifier = Modifier.size(18.dp), tint = ExpenseRed)
        }
    }
}
