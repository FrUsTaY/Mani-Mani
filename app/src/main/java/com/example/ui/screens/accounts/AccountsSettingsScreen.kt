package com.example.ui.screens.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.entity.AccountEntity
import com.example.service.AppThemeMode
import com.example.service.UserFinancePreferences
import com.example.ui.components.BankOfTheMonthCard
import com.example.ui.screens.GeminiApiKeyDialog
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.DateHelper
import com.example.ui.util.IconHelper
import com.example.ui.viewmodel.FinanceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSettingsScreen(
    viewModel: com.example.ui.viewmodel.FinanceViewModel,
    state: FinanceUiState,
    onAddAccountClick: () -> Unit,
    onManageCategories: () -> Unit = {},
    onEditAccount: (AccountEntity) -> Unit,
    onArchiveAccount: (AccountEntity) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onClearAllData: (keepAccountStructure: Boolean) -> Unit = {},
    onTogglePushNotifications: (Boolean) -> Unit = {},
    onSendTestPush: () -> Unit = {},
    onOpenBankSync: () -> Unit,
    onOpenGeminiAssistant: () -> Unit,
    onSaveGeminiApiKey: (String) -> Unit,
    onTestGeminiApiKey: (String, (Boolean, String) -> Unit) -> Unit,
    onClearGeminiApiKey: () -> Unit,
    onOpenPaydaySettings: () -> Unit = {},
    onOpenMe2MeTransfer: () -> Unit = {},
    onOpenIncomeDistribution: () -> Unit = {},
    onBankOfTheMonthSelect: (String) -> Unit = {},
    onApplyUserBankStructure: () -> Unit = {},
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showApplyStructureDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var keepAccountsZeroBalance by remember { mutableStateOf(true) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("accounts_settings_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // 0. Top Screen Header: "Счета и настройки" in ZenPlans style
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Счета и настройки",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
            }

            // 1. Accounts Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Счета (${state.accounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onAddAccountClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp).testTag("add_account_button_settings")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Создать счёт", fontSize = 13.sp)
                    }
                }
            }

            // Accounts List
            items(state.accounts, key = { it.id }) { acc ->
                val accColor = IconHelper.parseColor(acc.colorHex)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (acc.isArchived) 0.25f else 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIconByName(acc.iconName),
                                contentDescription = acc.name,
                                tint = accColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (acc.isArchived) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    ) {
                                        Text(
                                            text = "В архиве",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = CurrencyHelper.formatAmount(acc.balance, acc.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { onEditAccount(acc) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать счёт", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(onClick = { onArchiveAccount(acc) }) {
                            Icon(
                                imageVector = if (acc.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = if (acc.isArchived) "Восстановить" else "Архивировать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onDeleteAccount(acc) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить счёт", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // 2. Base Currency Preference
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Основная валюта приложения",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "В этой валюте рассчитывается общий баланс и статистика расходов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currencies = listOf("RUB", "USD", "EUR", "KZT", "BYN", "CNY")
                    currencies.forEach { curr ->
                        val isSelected = state.baseCurrency == curr
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCurrencyChange(curr) },
                            label = { Text("$curr ${CurrencyHelper.currencySymbols[curr] ?: ""}") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Theme Mode Preference
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Тема оформления",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Настройте светлый или тёмный режим приложения",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val isSelected = state.themeMode == mode
                        val icon = when (mode) {
                            AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            AppThemeMode.LIGHT -> Icons.Default.LightMode
                            AppThemeMode.DARK -> Icons.Default.DarkMode
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { onThemeModeChange(mode) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(mode.title) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_chip_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            // 3. Personal Banking & Payday Cycle Section
            item {
                Text(
                    text = "Банковская система и финансовый цикл",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_banking_ecosystem_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Payday setting row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenPaydaySettings() }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "День зарплаты: ${state.payday}-е число",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Цикл: ${state.paydayPeriodLabel} (${state.daysUntilPayday} дн. до ЗП)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = onOpenPaydaySettings) {
                                Text("Изменить")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Bank of the Month selector
                        Text(
                            text = "Основной банк месяца (кэшбэк):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        BankOfTheMonthCard(
                            currentBank = state.bankOfTheMonth,
                            onBankSelect = onBankOfTheMonthSelect
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick tool buttons
                        Text(
                            text = "Инструменты переводов:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenMe2MeTransfer,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TransferBlue)
                            ) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Перевод Me2Me", fontSize = 12.sp)
                            }

                            Button(
                                onClick = onOpenIncomeDistribution,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                            ) {
                                Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Шлюз ЗП", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showApplyStructureDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Применить структуру моих счетов", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 4. Gemini AI Assistant Section
            item {
                Text(
                    text = "ИИ-Помощник Gemini",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_gemini_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Google Gemini 3.5",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (state.isAiConfigured) "Ключ подключен" else "Требуется API ключ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (state.isAiConfigured) androidx.compose.ui.graphics.Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (state.isAiConfigured) {
                                Surface(
                                    color = androidx.compose.ui.graphics.Color(0xFFD1FAE5),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "АКТИВЕН",
                                        color = androidx.compose.ui.graphics.Color(0xFF065F46),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (state.isAiConfigured)
                                "ИИ анализирует расходы, цели и структуру бюджета, предлагая персональные финансовые инсайты."
                            else
                                "Введите ваш бесплатный API-ключ Gemini, чтобы ИИ мог анализировать траты, искать скрытые резервы и прогнозировать цели.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.isAiConfigured && state.geminiApiKeyMasked.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ключ: ${state.geminiApiKeyMasked}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenGeminiAssistant,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("open_gemini_assistant_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Открыть ИИ", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { showApiKeyDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("edit_gemini_key_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (state.isAiConfigured) "Ключ" else "Ввести ключ", fontSize = 13.sp)
                            }

                            if (state.isAiConfigured && state.userGeminiApiKey.isNotBlank()) {
                                IconButton(
                                    onClick = onClearGeminiApiKey,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Удалить ключ",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Data Management (Export & Reset)
            item {
                Text(
                    text = "Управление данными",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Экспорт/Импорт данных", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("clear_all_data_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Очистить все данные", fontWeight = FontWeight.SemiBold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 5. App Info Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "«Мани-мани» • Версия 1.0",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Умный нативный учёт личных финансов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }



    // Apply Structure Confirmation Dialog
    if (showApplyStructureDialog) {
        AlertDialog(
            onDismissRequest = { showApplyStructureDialog = false },
            title = { Text("Применить структуру счетов?") },
            text = { Text("ВНИМАНИЕ! Эта операция очистит текущую базу данных и применит вашу персонализированную структуру счетов (ВТБ, Т-Банк, Озон, Альфа, Яндекс). Все текущие транзакции будут удалены. Продолжить?") },
            confirmButton = {
                Button(onClick = {
                    onApplyUserBankStructure()
                    showApplyStructureDialog = false
                }) {
                    Text("Да, применить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showApplyStructureDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showExportDialog) {
        var showConfirmDialog by remember { mutableStateOf(false) }
        var isImportMode by remember { mutableStateOf(false) }
        var isCloudMode by remember { mutableStateOf(false) }
        var cloudToken by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(viewModel.userFinancePrefs.getYandexToken())) }

        val exportLocalLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                viewModel.exportBackupLocal(uri)
            }
            showExportDialog = false
        }

        val importLocalLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.importBackupLocal(uri)
            }
            showExportDialog = false
            showConfirmDialog = false
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text(if (isImportMode) "Подтверждение импорта" else "Подтверждение экспорта") },
                text = {
                    if (isImportMode) {
                        Text("ВНИМАНИЕ! Текущие данные будут ПОЛНОСТЬЮ перезаписаны данными из бэкапа. Продолжить?")
                    } else {
                        Text(if (isCloudMode) "Текущий облачный бэкап в Яндекс.Диске будет перезаписан. Продолжить?" else "Сохранить данные локально?")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (isImportMode) {
                            if (isCloudMode) {
                                viewModel.userFinancePrefs.setYandexToken(cloudToken.text.trim())
                                viewModel.importBackupCloud(cloudToken.text.trim())
                                showExportDialog = false
                            } else {
                                importLocalLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        } else {
                            if (isCloudMode) {
                                viewModel.userFinancePrefs.setYandexToken(cloudToken.text.trim())
                                viewModel.exportBackupCloud(cloudToken.text.trim())
                                showExportDialog = false
                            } else {
                                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(java.util.Date())
                                exportLocalLauncher.launch("manimani_backup_$timestamp.json")
                            }
                        }
                        showConfirmDialog = false
                    }) {
                        Text("Да")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showConfirmDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        Dialog(onDismissRequest = { showExportDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Экспорт / Импорт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Вы можете сохранить или восстановить все ваши данные, включая счета, операции, долги, копилки и настройки.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Локально", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { 
                            isImportMode = false
                            isCloudMode = false
                            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(java.util.Date())
                            exportLocalLauncher.launch("manimani_backup_$timestamp.json")
                        }, modifier = Modifier.weight(1f)) {
                            Text("Экспорт")
                        }
                        OutlinedButton(
                            onClick = {
                                isImportMode = true
                                isCloudMode = false
                                showConfirmDialog = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Импорт")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Яндекс.Диск", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = cloudToken,
                        onValueChange = { cloudToken = it },
                        label = { Text("API токен Яндекс.Диска") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { 
                            if (cloudToken.text.isNotBlank()) {
                                isImportMode = false
                                isCloudMode = true
                                showConfirmDialog = true
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Text("В облако")
                        }
                        Button(
                            onClick = {
                                if (cloudToken.text.isNotBlank()) {
                                    isImportMode = true
                                    isCloudMode = true
                                    showConfirmDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Из облака")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { showExportDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }

    // Clear All Data Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Очистить все данные?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Вы собираетесь очистить демонстрационные данные для начала реального учёта личных финансов:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Все тестовые операции и переводы будут удалены\n• Тестовые долги и накопления обнулятся\n• Категории расходов и доходов сохранятся",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keepAccountsZeroBalance = !keepAccountsZeroBalance }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = keepAccountsZeroBalance,
                                onCheckedChange = { keepAccountsZeroBalance = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Сохранить счета с балансом 0 ₽",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ВТБ, Т-Банк, Озон, Альфа и Яндекс будут обнулены для быстрого старта",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData(keepAccountsZeroBalance)
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Все данные очищены. Можно вести реальный учёт!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Очистить всё")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showApiKeyDialog) {
        GeminiApiKeyDialog(
            currentKey = state.userGeminiApiKey,
            maskedKey = state.geminiApiKeyMasked,
            isConfigured = state.isAiConfigured,
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                onSaveGeminiApiKey(key)
                showApiKeyDialog = false
                Toast.makeText(context, "Ключ Gemini сохранен", Toast.LENGTH_SHORT).show()
            },
            onTest = onTestGeminiApiKey
        )
    }
}

