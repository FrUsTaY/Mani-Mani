package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.entity.CategoryEntity
import com.example.ui.util.IconHelper

@Composable
fun AddEditCategoryDialog(
    categoryToEdit: CategoryEntity? = null,
    defaultType: String = "EXPENSE",
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, iconName: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(categoryToEdit?.name ?: "") }
    var type by remember { mutableStateOf(categoryToEdit?.type ?: defaultType) }
    var iconName by remember { mutableStateOf(categoryToEdit?.iconName ?: "shopping_cart") }
    var colorHex by remember { mutableStateOf(categoryToEdit?.colorHex ?: "#FF5722") }

    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (categoryToEdit == null) "Создать категорию" else "Редактировать категорию") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Icon and Color Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(IconHelper.parseColor(colorHex).copy(alpha = 0.2f))
                                .clickable { showPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIconByName(iconName),
                                contentDescription = "Выбрать иконку",
                                tint = IconHelper.parseColor(colorHex),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Выбрать цвет и иконку", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название категории") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (categoryToEdit == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Тип", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == "EXPENSE",
                            onClick = { type = "EXPENSE" },
                            label = { Text("Расход") }
                        )
                        FilterChip(
                            selected = type == "INCOME",
                            onClick = { type = "INCOME" },
                            label = { Text("Доход") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), type, iconName, colorHex)
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )

    if (showPicker) {
        IconColorPickerBottomSheet(
            initialColorHex = colorHex,
            initialIconName = iconName,
            onDismiss = { showPicker = false },
            onSave = { newColor, newIcon ->
                colorHex = newColor
                iconName = newIcon
                showPicker = false
            }
        )
    }
}
