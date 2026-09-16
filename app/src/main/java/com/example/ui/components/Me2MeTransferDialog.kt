package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.AccountEntity
import com.example.service.UserBankHelper
import com.example.ui.theme.TransferBlue
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper

@Composable
fun Me2MeTransferDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (fromAccountId: Long, toAccountId: Long, amount: Double, note: String) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }

    var selectedFromId by remember {
        mutableStateOf(activeAccounts.firstOrNull()?.id ?: 0L)
    }
    var selectedToId by remember {
        mutableStateOf(activeAccounts.getOrNull(1)?.id ?: activeAccounts.firstOrNull()?.id ?: 0L)
    }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fromAccount = activeAccounts.find { it.id == selectedFromId }
    val toAccount = activeAccounts.find { it.id == selectedToId }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .testTag("me2me_transfer_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TransferBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = null,
                                tint = TransferBlue
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Перевод между своими счетами",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Me2Me без комиссии и без искажения расходов",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Route Presets
                Text(
                    text = "Частые маршруты между вашими банками:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(UserBankHelper.POPULAR_TRANSFER_ROUTES) { route ->
                        val srcAcc = UserBankHelper.findSourceAccount(activeAccounts, route.sourceKeyword)
                        val trgAcc = UserBankHelper.findTargetAccount(activeAccounts, route.targetKeyword)
                        val isAvailable = srcAcc != null && trgAcc != null && srcAcc.id != trgAcc.id
                        val isCurrentlySelected = srcAcc?.id == selectedFromId && trgAcc?.id == selectedToId

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrentlySelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isCurrentlySelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable(enabled = isAvailable) {
                                if (srcAcc != null && trgAcc != null) {
                                    selectedFromId = srcAcc.id
                                    selectedToId = trgAcc.id
                                    if (noteText.isBlank() || noteText == "Перевод между своими счетами") {
                                        noteText = route.defaultNote
                                    }
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(text = route.iconEmoji, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = route.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = route.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*([.,]\d{0,2})?$"""))) {
                            amountText = input.replace(',', '.')
                            errorMessage = null
                        }
                    },
                    label = { Text("Сумма перевода") },
                    placeholder = { Text("0.00") },
                    trailingIcon = {
                        Text(
                            text = "₽",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TransferBlue,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TransferBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("me2me_amount_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                // Quick Amount Chips
                Spacer(modifier = Modifier.height(8.dp))
                val quickAmounts = listOf(1000.0, 3000.0, 5000.0, 10000.0, 20000.0)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickAmounts) { qa ->
                        AssistChip(
                            onClick = { amountText = "%.0f".format(qa) },
                            label = { Text(CurrencyHelper.formatAmount(qa, "RUB")) }
                        )
                    }
                    if (fromAccount != null && fromAccount.balance > 0) {
                        item {
                            AssistChip(
                                onClick = { amountText = "%.0f".format(fromAccount.balance) },
                                label = { Text("Всё: ${CurrencyHelper.formatAmount(fromAccount.balance, "RUB")}") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account From / To Visual Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // FROM Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Откуда (списать)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fromAccount?.name ?: "Не выбран",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Остаток: ${CurrencyHelper.formatAmount(fromAccount?.balance ?: 0.0, fromAccount?.currency ?: "RUB")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = TransferBlue
                    )

                    // TO Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Куда (зачислить)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = toAccount?.name ?: "Не выбран",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Остаток: ${CurrencyHelper.formatAmount(toAccount?.balance ?: 0.0, toAccount?.currency ?: "RUB")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selector for FROM account
                Text(
                    text = "Счёт списания:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(activeAccounts) { acc ->
                        val isSelected = acc.id == selectedFromId
                        val accColor = IconHelper.parseColor(acc.colorHex)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable {
                                selectedFromId = acc.id
                                if (selectedToId == acc.id) {
                                    selectedToId = activeAccounts.firstOrNull { it.id != acc.id }?.id ?: 0L
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accColor))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selector for TO account
                Text(
                    text = "Счёт зачисления:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(activeAccounts.filter { it.id != selectedFromId }) { acc ->
                        val isSelected = acc.id == selectedToId
                        val accColor = IconHelper.parseColor(acc.colorHex)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary) else null,
                            modifier = Modifier.clickable { selectedToId = acc.id }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accColor))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Note Field
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Заметка к переводу") },
                    placeholder = { Text("Например: пополнение продуктового счёта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (amount == null || amount <= 0.0) {
                                errorMessage = "Введите корректную сумму перевода"
                                return@Button
                            }
                            if (selectedFromId == selectedToId) {
                                errorMessage = "Выберите разные счета списания и зачисления"
                                return@Button
                            }
                            onConfirm(
                                selectedFromId,
                                selectedToId,
                                amount,
                                noteText.ifBlank { "Перевод между своими счетами" }
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("confirm_me2me_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TransferBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Перевести")
                    }
                }
            }
        }
    }
}
