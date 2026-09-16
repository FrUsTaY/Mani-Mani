package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.AccountEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.DebtEntity
import com.example.data.entity.GoalEntity
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DistributionTemplate(
    val id: String,
    val name: String,
    val items: List<AllocationItemData>
)

object TemplateStorage {
    private const val PREFS_NAME = "distribution_templates_prefs"
    private const val KEY_TEMPLATES = "templates_json"

    fun loadTemplates(context: Context): List<DistributionTemplate> {
        val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TEMPLATES, null) ?: return emptyList()
        val list = mutableListOf<DistributionTemplate>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val itemsArr = obj.getJSONArray("items")
                val itemsList = mutableListOf<AllocationItemData>()
                for (j in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(j)
                    itemsList.add(
                        AllocationItemData(
                            type = AllocationTargetType.valueOf(itemObj.getString("type")),
                            targetId = itemObj.getLong("targetId"),
                            amount = itemObj.getDouble("amount")
                        )
                    )
                }
                list.add(DistributionTemplate(obj.getString("id"), obj.getString("name"), itemsList))
            }
        } catch (e: Exception) {}
        return list
    }

    fun saveTemplates(context: Context, templates: List<DistributionTemplate>) {
        val array = JSONArray()
        for (t in templates) {
            val tObj = JSONObject()
            tObj.put("id", t.id)
            tObj.put("name", t.name)
            val itemsArr = JSONArray()
            for (item in t.items) {
                val itemObj = JSONObject()
                itemObj.put("type", item.type.name)
                itemObj.put("targetId", item.targetId)
                itemObj.put("amount", item.amount)
                itemsArr.put(itemObj)
            }
            tObj.put("items", itemsArr)
            array.put(tObj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_TEMPLATES, array.toString()).apply()
    }
}

@Composable
fun IncomeDistributionDialog(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    goals: List<GoalEntity>,
    debts: List<DebtEntity>,
    onDismiss: () -> Unit,
    onAddCategory: (name: String, type: String, iconName: String, colorHex: String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onConfirm: (sourceAccountId: Long, allocations: List<AllocationItemData>) -> Unit
) {
    val context = LocalContext.current
    var templates by remember { mutableStateOf(TemplateStorage.loadTemplates(context)) }
    
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    val expenseCategories = remember(categories) { categories.filter { it.type == "EXPENSE" } }
    val activeDebts = remember(debts) { debts.filter { !it.isSettled } }

    val defaultSource = remember(activeAccounts) {
        activeAccounts.find { it.name.contains("Т-Банк", ignoreCase = true) || it.name.contains("Тинькофф", ignoreCase = true) }
            ?: activeAccounts.find { it.name.contains("Зарплат", ignoreCase = true) }
            ?: activeAccounts.firstOrNull()
    }
    
    var selectedSourceId by remember { mutableStateOf(defaultSource?.id ?: 0L) }
    var totalAmountText by remember(selectedSourceId) { 
        mutableStateOf(activeAccounts.find { it.id == selectedSourceId }?.balance?.let { if (it > 0) it.toString().removeSuffix(".0") else "" } ?: "") 
    }
    
    // List of allocations
    val allocations = remember { mutableStateListOf<AllocationItemData>() }
    
    var showTemplateSaveDialog by remember { mutableStateOf(false) }
    var showTargetSelectorDialog by remember { mutableStateOf<Int?>(null) } // index of item being edited
    var showQuickAddCategory by remember { mutableStateOf(false) }
    var showManageCategories by remember { mutableStateOf(false) }
    
    val totalAllocated = allocations.sumOf { it.amount }
    val totalToDistribute = totalAmountText.toDoubleOrNull() ?: 0.0
    val remaining = totalToDistribute - totalAllocated

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
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
                    Text("Распределение средств", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
                }

                // Total & Source
                OutlinedTextField(
                    value = totalAmountText,
                    onValueChange = { input -> 
                        if (input.isEmpty() || input.matches(Regex("""^\d*([.,]\d{0,2})?$"""))) {
                            totalAmountText = input.replace(',', '.')
                        }
                    },
                    label = { Text("Сумма к распределению (Осталось: ${CurrencyHelper.formatAmount(remaining, "RUB")})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Откуда распределять:", style = MaterialTheme.typography.labelSmall)
                var sourceDropdownExpanded by remember { mutableStateOf(false) }
                val selectedSourceName = activeAccounts.find { it.id == selectedSourceId }?.name ?: "Выберите счет"
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { sourceDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedSourceName)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }) {
                        activeAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = { selectedSourceId = acc.id; sourceDropdownExpanded = false }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Templates
                Text("Шаблоны распределения", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    item {
                        SuggestionChip(
                            onClick = { showTemplateSaveDialog = true },
                            label = { Text("+ Сохранить текущий") }
                        )
                    }
                    items(templates) { tmpl ->
                        InputChip(
                            selected = false,
                            onClick = {
                                allocations.clear()
                                allocations.addAll(tmpl.items.map { it.copy() }) // deep copy
                            },
                            label = { Text(tmpl.name) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Удалить", modifier = Modifier.size(16.dp).clickable {
                                    templates = templates.filter { it.id != tmpl.id }
                                    TemplateStorage.saveTemplates(context, templates)
                                })
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Remaining Banner
                Surface(
                    color = if (remaining < 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Осталось:", fontWeight = FontWeight.Bold)
                        Text(CurrencyHelper.formatAmount(remaining, "RUB"), fontWeight = FontWeight.Bold, color = if(remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Allocations List
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(allocations) { index, alloc ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Target Info
                                Column(modifier = Modifier.weight(1f).clickable { showTargetSelectorDialog = index }) {
                                    val targetName = when(alloc.type) {
                                        AllocationTargetType.ACCOUNT -> activeAccounts.find { it.id == alloc.targetId }?.name ?: "Счет"
                                        AllocationTargetType.CATEGORY -> expenseCategories.find { it.id == alloc.targetId }?.name ?: "Категория"
                                        AllocationTargetType.GOAL -> goals.find { it.id == alloc.targetId }?.name ?: "Копилка"
                                        AllocationTargetType.DEBT -> activeDebts.find { it.id == alloc.targetId }?.personName ?: "Долг"
                                    }
                                    val icon = when(alloc.type) {
                                        AllocationTargetType.ACCOUNT -> Icons.Default.AccountBalanceWallet
                                        AllocationTargetType.CATEGORY -> Icons.Default.ShoppingCart
                                        AllocationTargetType.GOAL -> Icons.Default.Savings
                                        AllocationTargetType.DEBT -> Icons.Default.MoneyOff
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(targetName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                
                                // Amount Input
                                var amountText by remember(alloc) { mutableStateOf(if (alloc.amount == 0.0) "" else alloc.amount.toString().removeSuffix(".0")) }
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.matches(Regex("""^\d*([.,]\d{0,2})?$"""))) {
                                            amountText = input.replace(',', '.')
                                            alloc.amount = amountText.toDoubleOrNull() ?: 0.0
                                            allocations[index] = alloc.copy() // trigger recomposition
                                        }
                                    },
                                    modifier = Modifier.width(100.dp).height(50.dp),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                
                                IconButton(onClick = { allocations.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, "Удалить", tint = ExpenseRed)
                                }
                            }
                        }
                    }
                    
                    item {
                        TextButton(
                            onClick = {
                                // Add empty row
                                val firstCat = expenseCategories.firstOrNull()
                                if (firstCat != null) {
                                    allocations.add(AllocationItemData(AllocationTargetType.CATEGORY, firstCat.id, 0.0))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Добавить статью расходов")
                        }
                    }
                }
                
                // Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = { onConfirm(selectedSourceId, allocations.toList()) },
                        enabled = allocations.isNotEmpty() && totalAllocated > 0 && remaining >= 0,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                    ) {
                        Text("Распределить")
                    }
                }
            }
        }
    }
    
    if (showTemplateSaveDialog) {
        var templateName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTemplateSaveDialog = false },
            title = { Text("Сохранить шаблон") },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("Название шаблона (напр. Аванс)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (templateName.isNotBlank()) {
                        val newTemplate = DistributionTemplate(
                            id = UUID.randomUUID().toString(),
                            name = templateName,
                            items = allocations.map { it.copy() }
                        )
                        templates = templates + newTemplate
                        TemplateStorage.saveTemplates(context, templates)
                        showTemplateSaveDialog = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateSaveDialog = false }) { Text("Отмена") }
            }
        )
    }
    
    if (showTargetSelectorDialog != null) {
        val index = showTargetSelectorDialog!!
        val currentItem = allocations[index]
        AlertDialog(
            onDismissRequest = { showTargetSelectorDialog = null },
            title = { Text("Куда отправить деньги?") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    item { Text("Счета", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    items(activeAccounts) { acc ->
                        Text(
                            acc.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                allocations[index] = currentItem.copy(type = AllocationTargetType.ACCOUNT, targetId = acc.id)
                                showTargetSelectorDialog = null
                            }.padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                    item { Text("Категории", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                    items(expenseCategories) { cat ->
                        Text(
                            cat.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                allocations[index] = currentItem.copy(type = AllocationTargetType.CATEGORY, targetId = cat.id)
                                showTargetSelectorDialog = null
                            }.padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                    item {
                        Text(
                            "+ Создать новую категорию",
                            modifier = Modifier.fillMaxWidth().clickable {
                                showTargetSelectorDialog = null
                                showQuickAddCategory = true
                            }.padding(vertical = 8.dp, horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    item {
                        Text(
                            "Управление категориями",
                            modifier = Modifier.fillMaxWidth().clickable {
                                showTargetSelectorDialog = null
                                showManageCategories = true
                            }.padding(vertical = 8.dp, horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (activeDebts.isNotEmpty()) {
                        item { Text("Долги / Кредиты", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                        items(activeDebts) { debt ->
                            Text(
                                "${debt.personName} (${CurrencyHelper.formatAmount(debt.amount, "RUB")})",
                                modifier = Modifier.fillMaxWidth().clickable {
                                    allocations[index] = currentItem.copy(type = AllocationTargetType.DEBT, targetId = debt.id)
                                    showTargetSelectorDialog = null
                                }.padding(vertical = 8.dp, horizontal = 12.dp)
                            )
                        }
                    }
                    item { Text("Копилки / Цели", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                    items(goals) { goal ->
                        Text(
                            goal.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                allocations[index] = currentItem.copy(type = AllocationTargetType.GOAL, targetId = goal.id)
                                showTargetSelectorDialog = null
                            }.padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTargetSelectorDialog = null }) { Text("Закрыть") }
            }
        )
    }

    if (showQuickAddCategory) {
        AddEditCategoryDialog(
            categoryToEdit = null,
            defaultType = "EXPENSE",
            onDismiss = { showQuickAddCategory = false },
            onSave = { name, type, iconName, colorHex ->
                onAddCategory(name, type, iconName, colorHex)
                showQuickAddCategory = false
            }
        )
    }

    if (showManageCategories) {
        ManageCategoriesDialog(
            categories = categories,
            onDismiss = { showManageCategories = false },
            onUpdateCategory = onUpdateCategory,
            onDeleteCategory = onDeleteCategory,
            onAddCategory = onAddCategory
        )
    }
}
