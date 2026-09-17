package com.example.ui.screens.sync

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.GoalEntity
import com.example.data.entity.PendingNotificationEntity
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.NotificationPermissionHelper
import com.example.ui.viewmodel.FinanceUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSyncScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onConfirmNotification: (PendingNotificationEntity, Long, Long?, Long?, Long?, String) -> Unit,
    onDismissNotification: (PendingNotificationEntity) -> Unit,
    onParseManualText: (String) -> Unit,
    onTogglePushNotifications: (Boolean) -> Unit = {},
    onSendTestPush: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isListenerEnabled by remember {
        mutableStateOf(NotificationPermissionHelper.isNotificationListenerEnabled(context))
    }

    // Manual test text input dialog
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }

    val accountsMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val categoriesMap = remember(state.categories) { state.categories.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Синхронизация с банками", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("bank_sync_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isListenerEnabled = NotificationPermissionHelper.isNotificationListenerEnabled(context)
                        },
                        modifier = Modifier.testTag("refresh_permission_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить статус")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize().testTag("bank_sync_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Permission status card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isListenerEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("service_status_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isListenerEnabled) IncomeGreen else ExpenseRed
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isListenerEnabled) Icons.Default.Check else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isListenerEnabled) "Служба пуш-уведомлений активна" else "Требуется доступ к уведомлениям",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (isListenerEnabled) {
                                        "Приложение автоматически ловит банковские пуши от Сбера, Т-Банка, Альфы, ВТБ и др."
                                    } else {
                                        "Для автоматического перехвата пуш-уведомлений от приложений банков включите системное разрешение"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isListenerEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = NotificationPermissionHelper.getNotificationListenerSettingsIntent()
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("enable_listener_settings_button")
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Открыть настройки Android")
                            }
                        }
                    }
                }
            }

            // 2. Quick actions: paste from clipboard or manual test
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Быстрое распознавание текста",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Вставьте скопированный текст SMS или пуша от банка, чтобы распознать операцию",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        if (text.isNotBlank()) {
                                            onParseManualText(text)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("paste_clipboard_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Из буфера")
                            }

                            OutlinedButton(
                                onClick = { showManualInputDialog = true },
                                modifier = Modifier.weight(1f).testTag("test_custom_text_button")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ввести текст")
                            }
                        }
                    }
                }
            }

            // 3. Pending notifications queue
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Входящие операции (${state.pendingNotifications.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (state.pendingNotifications.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Нет необработанных уведомлений",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Как только банк пришлёт пуш или вы вставите текст, они появятся здесь",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                items(state.pendingNotifications, key = { it.id }) { notif ->
                    PendingNotificationCard(
                        notification = notif,
                        accounts = state.accounts,
                        categories = state.categories,
                        goals = state.goals,
                        onConfirm = { accId, catId, toAccId, goalId, type ->
                            onConfirmNotification(notif, accId, catId, toAccId, goalId, type)
                        },
                        onDismiss = {
                            onDismissNotification(notif)
                        }
                    )
                }
            }
        }
    }

    // Manual input dialog
    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = { Text("Тестовый ввод сообщения") },
            text = {
                Column {
                    Text(
                        text = "Введите текст уведомления банка или выберите готовый пример:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        placeholder = { Text("Покупка 1450 ₽ в ВкусВилл. Карта *1234") },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("manual_notification_text_field"),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Готовые примеры:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                manualText = "Покупка 1 250 ₽ в ВкусВилл. Карта *1234. Доступно 14 500 ₽"
                            },
                            label = { Text("ВкусВилл", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                manualText = "Зачисление зарплаты 95 000 ₽ на карту *1234"
                            },
                            label = { Text("Зарплата", fontSize = 11.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualText.isNotBlank()) {
                            onParseManualText(manualText)
                            manualText = ""
                            showManualInputDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_manual_parse_button")
                ) {
                    Text("Распознать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun PendingNotificationCard(
    notification: PendingNotificationEntity,
    accounts: List<com.example.data.entity.AccountEntity>,
    categories: List<com.example.data.entity.CategoryEntity>,
    goals: List<GoalEntity> = emptyList(),
    onConfirm: (accountId: Long, categoryId: Long?, toAccountId: Long?, goalId: Long?, type: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember(notification) {
        mutableStateOf(notification.type)
    }
    var selectedAccountId by remember(notification) {
        mutableStateOf(notification.suggestedAccountId ?: accounts.firstOrNull()?.id ?: 1L)
    }
    var selectedCategoryId by remember(notification, selectedType) {
        val typeCats = categories.filter { it.type == selectedType }
        mutableStateOf(
            if (notification.suggestedCategoryId != null && typeCats.any { it.id == notification.suggestedCategoryId }) {
                notification.suggestedCategoryId
            } else {
                typeCats.firstOrNull()?.id
            }
        )
    }
    var selectedToAccountId by remember(notification) {
        val otherAccount = accounts.firstOrNull { it.id != (notification.suggestedAccountId ?: accounts.firstOrNull()?.id ?: 1L) }
        mutableStateOf<Long?>(otherAccount?.id)
    }
    var selectedGoalId by remember(notification) {
        mutableStateOf<Long?>(null)
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_notification_card_${notification.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Bank badge & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = notification.bankName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = timeFormat.format(Date(notification.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount and Merchant
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.merchantOrSender.ifBlank { "Операция" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    notification.cardLast4?.let { last4 ->
                        Text(
                            text = "Карта *$last4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val amountColor = when (selectedType) {
                    "INCOME" -> IncomeGreen
                    "TRANSFER" -> MaterialTheme.colorScheme.primary
                    else -> ExpenseRed
                }
                val amountPrefix = when (selectedType) {
                    "INCOME" -> "+"
                    "TRANSFER" -> "⇄ "
                    else -> "-"
                }

                Text(
                    text = "$amountPrefix${CurrencyHelper.format(notification.amount, notification.currency)}",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                    color = amountColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "\"${notification.rawText}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Type selector chips (Расход / Перевод / Доход)
            Text(
                text = "Тип операции:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == "EXPENSE",
                    onClick = { selectedType = "EXPENSE" },
                    label = { Text("Расход", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedType == "TRANSFER",
                    onClick = { selectedType = "TRANSFER" },
                    label = { Text("Перевод", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedType == "INCOME",
                    onClick = { selectedType = "INCOME" },
                    label = { Text("Доход", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Account & Category (or Destination) selector chips
            if (selectedType == "TRANSFER") {
                Text(
                    text = "Куда записать перевод:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Source Account selector
                    var showSourceMenu by remember { mutableStateOf(false) }
                    val currentSourceAccount = accounts.find { it.id == selectedAccountId }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showSourceMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "От: ${currentSourceAccount?.name ?: "Счёт"}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = showSourceMenu,
                            onDismissRequest = { showSourceMenu = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currency)})") },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        if (selectedToAccountId == acc.id) {
                                            selectedToAccountId = accounts.firstOrNull { it.id != acc.id }?.id
                                        }
                                        showSourceMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Target Account / Goal selector
                    var showTargetMenu by remember { mutableStateOf(false) }
                    val currentTargetText = when {
                        selectedGoalId != null -> goals.find { it.id == selectedGoalId }?.let { "🎯 ${it.name}" } ?: "Копилка"
                        selectedToAccountId != null -> accounts.find { it.id == selectedToAccountId }?.let { "До: ${it.name}" } ?: "Куда"
                        else -> "Куда"
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showTargetMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentTargetText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = showTargetMenu,
                            onDismissRequest = { showTargetMenu = false }
                        ) {
                            val targetAccounts = accounts.filter { it.id != selectedAccountId }
                            if (targetAccounts.isNotEmpty()) {
                                Text(
                                    text = "  Счета:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                                targetAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currency)})") },
                                        onClick = {
                                            selectedToAccountId = acc.id
                                            selectedGoalId = null
                                            showTargetMenu = false
                                        }
                                    )
                                }
                            }
                            if (goals.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    text = "  Копилки / Цели:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                                goals.forEach { goal ->
                                    DropdownMenuItem(
                                        text = { Text("🎯 ${goal.name}") },
                                        onClick = {
                                            selectedGoalId = goal.id
                                            selectedToAccountId = null
                                            showTargetMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Куда записать:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Account selector
                    var showAccountMenu by remember { mutableStateOf(false) }
                    val currentAccount = accounts.find { it.id == selectedAccountId }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showAccountMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentAccount?.name ?: "Счёт",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${CurrencyHelper.format(acc.balance, acc.currency)})") },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        showAccountMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Category selector
                    var showCategoryMenu by remember { mutableStateOf(false) }
                    val currentCategory = categories.find { it.id == selectedCategoryId }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showCategoryMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentCategory?.name ?: "Категория",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            categories.filter { it.type == selectedType }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_notification_${notification.id}")
                ) {
                    Text("Пропустить")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(
                            selectedAccountId,
                            if (selectedType != "TRANSFER") selectedCategoryId else null,
                            if (selectedType == "TRANSFER") selectedToAccountId else null,
                            if (selectedType == "TRANSFER") selectedGoalId else null,
                            selectedType
                        )
                    },
                    modifier = Modifier.testTag("confirm_notification_${notification.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
        }
    }
}
