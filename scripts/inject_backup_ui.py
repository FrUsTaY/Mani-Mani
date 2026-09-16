import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

# Fix the button
content = content.replace(
    'Icon(Icons.Default.FileDownload, contentDescription = null)\n                    Spacer(modifier = Modifier.width(8.dp))\n                    Text("Экспорт операций в CSV", fontWeight = FontWeight.SemiBold)',
    'Icon(Icons.Default.Save, contentDescription = null)\n                    Spacer(modifier = Modifier.width(8.dp))\n                    Text("Экспорт/Импорт данных", fontWeight = FontWeight.SemiBold)'
)
content = content.replace('var showExportDialog by remember { mutableStateOf(false) }', 'var showExportDialog by remember { mutableStateOf(false) }')

# Now inject the dialog component
# Find the start of CSV Export Dialog
csv_dialog_start = content.find('    // CSV Export Dialog')
if csv_dialog_start != -1:
    clear_dialog_start = content.find('    // Clear All Data Confirmation Dialog')
    
    backup_dialog_code = """
    if (showExportDialog) {
        var showConfirmDialog by remember { mutableStateOf(false) }
        var isImportMode by remember { mutableStateOf(false) }
        var isCloudMode by remember { mutableStateOf(false) }
        var cloudToken by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }

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
                                viewModel.importBackupCloud(cloudToken.text.trim())
                                showExportDialog = false
                            } else {
                                importLocalLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        } else {
                            if (isCloudMode) {
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
\n"""
    
    content = content[:csv_dialog_start] + backup_dialog_code + content[clear_dialog_start:]
    
    # We must ensure `viewModel` is passed or available where `showExportDialog` is invoked.
    # The `AccountsSettingsScreen` function signature already has `viewModel: FinanceViewModel`. Let's check.

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(content)

