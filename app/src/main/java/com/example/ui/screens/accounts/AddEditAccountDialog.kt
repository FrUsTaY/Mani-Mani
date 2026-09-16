package com.example.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.entity.AccountEntity
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper

@Composable
fun AddEditAccountDialog(
    initialAccount: AccountEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: String,
        balance: Double,
        currency: String,
        colorHex: String,
        iconName: String,
        includeInTotal: Boolean,
        includeInAnalytics: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var selectedType by remember { mutableStateOf(initialAccount?.type ?: "DEBIT") }
    var balanceText by remember { mutableStateOf(initialAccount?.balance?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "0") }
    var selectedCurrency by remember { mutableStateOf(initialAccount?.currency ?: "RUB") }
    var selectedColor by remember { mutableStateOf(initialAccount?.colorHex ?: "#3B82F6") }
    var selectedIcon by remember { mutableStateOf(initialAccount?.iconName ?: "credit_card") }
    var includeInTotal by remember { mutableStateOf(initialAccount?.includeInTotal ?: true) }
    var includeInAnalytics by remember { mutableStateOf(initialAccount?.includeInAnalytics ?: true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val accountTypes = listOf(
        "DEBIT" to "Карта",
        "CASH" to "Наличные",
        "DEPOSIT" to "Вклад/Копилка",
        "CREDIT" to "Кредитная карта",
        "INVESTMENT" to "Инвестиции",
        "CRYPTO" to "Крипто"
    )

    val colorOptions = listOf(
        "#3B82F6", "#10B981", "#F59E0B", "#EF4444",
        "#8B5CF6", "#EC4899", "#06B6D4", "#64748B"
    )

    val iconOptions = listOf(
        "credit_card" to "Карта",
        "payments" to "Наличные",
        "account_balance_wallet" to "Кошелёк",
        "savings" to "Копилка",
        "account_balance" to "Банк",
        "trending_up" to "Инвестиции"
    )

    val currencyOptions = listOf("RUB", "USD", "EUR", "KZT", "BYN", "CNY")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("add_edit_account_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialAccount == null) "Новый счёт" else "Редактировать счёт",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Название счёта") },
                    placeholder = { Text("Например: Т-Банк Black или Наличные") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_name_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Initial Balance
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("""^-?\d*([.,]\d{0,2})?$"""))) {
                            balanceText = it.replace(',', '.')
                            errorMessage = null
                        }
                    },
                    label = { Text("Текущий баланс") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("account_balance_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type
                Text(
                    text = "Тип счёта",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accountTypes) { (typeKey, typeLabel) ->
                        val isSelected = selectedType == typeKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedType = typeKey },
                            label = { Text(typeLabel) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Currency
                Text(
                    text = "Валюта счёта",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(currencyOptions) { curr ->
                        val isSelected = selectedCurrency == curr
                        val symbol = CurrencyHelper.currencySymbols[curr] ?: curr
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCurrency = curr },
                            label = { Text("$curr ($symbol)") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color Selection
                Text(
                    text = "Цвет",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorOptions.forEach { hex ->
                        val color = IconHelper.parseColor(hex)
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Selection
                Text(
                    text = "Иконка",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(iconOptions) { (iconKey, _) ->
                        val isSelected = selectedIcon == iconKey
                        val iconVector = IconHelper.getIconByName(iconKey)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { selectedIcon = iconKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = iconKey,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Включить в общий баланс", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Учитывать средства в общей сумме капитала",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = includeInTotal,
                        onCheckedChange = { includeInTotal = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Учитывать в аналитике", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "В доходах и расходах (отключите для копилок)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = includeInAnalytics,
                        onCheckedChange = { includeInAnalytics = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Введите название счёта"
                                return@Button
                            }
                            val bal = balanceText.toDoubleOrNull() ?: 0.0
                            onSave(name.trim(), selectedType, bal, selectedCurrency, selectedColor, selectedIcon, includeInTotal, includeInAnalytics)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.2f).height(48.dp).testTag("save_account_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
