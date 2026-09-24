package com.example.ui.screens.receipt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ReceiptApiKeyDialog(
    initialKey: String = "",
    onSaveKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(initialKey) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "API-ключ «Проверка чека»",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Для получения данных фискального чека из ФНС необходим API-ключ сервиса proverkacheka.com.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API-ключ") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Скрыть" else "Показать"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipt_api_key_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyInput.isNotBlank()) {
                        onSaveKey(keyInput.trim())
                        onDismiss()
                    }
                },
                enabled = keyInput.isNotBlank(),
                modifier = Modifier.testTag("save_receipt_api_key_button")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_receipt_api_key_button")
            ) {
                Text("Отмена")
            }
        }
    )
}
