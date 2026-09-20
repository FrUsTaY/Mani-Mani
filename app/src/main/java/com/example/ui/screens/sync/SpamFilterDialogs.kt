package com.example.ui.screens.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSpamRuleDialog(
    rawText: String,
    onDismiss: () -> Unit,
    onConfirm: (keyword: String) -> Unit
) {
    // Extract candidate phrases from raw text
    val candidateChips = remember(rawText) {
        val candidates = mutableListOf<String>()
        val lower = rawText.lowercase()

        // Well-known marketing phrases
        val patterns = listOf(
            "оформите кредит",
            "вам доступно",
            "одобрен кредит",
            "кредитная карта",
            "подайте заявку",
            "лимит по карте",
            "кэшбэк до",
            "ставка от",
            "рассрочка",
            "откройте вклад",
            "специальное предложение",
            "выгодное предложение"
        )
        for (p in patterns) {
            if (lower.contains(p)) {
                candidates.add(p)
            }
        }

        // If no patterns found, suggest parts of the text
        if (candidates.isEmpty()) {
            val words = rawText.split(Regex("""\s+""")).filter { it.length > 3 && !it.contains(Regex("""\d""")) }
            if (words.size >= 2) {
                candidates.add("${words[0]} ${words[1]}".lowercase())
            }
            words.take(3).forEach { candidates.add(it.lowercase()) }
        }
        candidates.distinct().take(5)
    }

    var selectedKeyword by remember { mutableStateOf(candidateChips.firstOrNull() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("В спам-фильтр", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Выберите или введите ключевую фразу, чтобы блокировать подобные рекламные уведомления в будущем:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (candidateChips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Быстрые фразы из текста:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidateChips.forEach { chip ->
                            FilterChip(
                                selected = selectedKeyword.equals(chip, ignoreCase = true),
                                onClick = { selectedKeyword = chip },
                                label = { Text(chip, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = selectedKeyword,
                    onValueChange = { selectedKeyword = it },
                    label = { Text("Стоп-фраза для блокировки") },
                    placeholder = { Text("например: вам доступно") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedKeyword.isNotBlank()) {
                        onConfirm(selectedKeyword.trim())
                    }
                },
                enabled = selectedKeyword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Заблокировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ManageSpamRulesDialog(
    spamKeywords: Set<String>,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newKeywordText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Спам-фильтр и правила", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    text = "Уведомления банков, содержащие эти фразы, автоматически отсекаются и не создают ложных расходов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeywordText,
                        onValueChange = { newKeywordText = it },
                        placeholder = { Text("Новая фраза...", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newKeywordText.isNotBlank()) {
                                onAddKeyword(newKeywordText.trim())
                                newKeywordText = ""
                            }
                        },
                        enabled = newKeywordText.isNotBlank()
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (spamKeywords.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пользовательских правил пока нет.\nБазовые рекламные паттерны банков уже блокируются автоматически.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(spamKeywords.toList(), key = { it }) { keyword ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = keyword,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onRemoveKeyword(keyword) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
