package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.service.UserFinancePreferences

@Composable
fun BankOfTheMonthCard(
    currentBank: String,
    onBankSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isYandex = currentBank == UserFinancePreferences.BANK_YANDEX

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isYandex) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isYandex) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B)) else null,
        modifier = modifier
            .fillMaxWidth()
            .testTag("bank_of_the_month_card")
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isYandex) Color(0xFFF59E0B).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isYandex) Icons.Default.Loyalty else Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isYandex) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Основной банк месяца",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isYandex) "Яндекс Банк (повышенный кэшбэк)" else "ВТБ (стандартный режим)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bank Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // VTB Option
                val isVtbSelected = !isYandex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isVtbSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onBankSelect(UserFinancePreferences.BANK_VTB) }
                        .padding(vertical = 10.dp)
                        .testTag("select_vtb_bank_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ВТБ (Основной)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isVtbSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isVtbSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Yandex Bank Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isYandex) Color(0xFFF59E0B) else Color.Transparent)
                        .clickable { onBankSelect(UserFinancePreferences.BANK_YANDEX) }
                        .padding(vertical = 10.dp)
                        .testTag("select_yandex_bank_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Яндекс Банк ✨",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isYandex) FontWeight.Bold else FontWeight.Medium,
                        color = if (isYandex) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isYandex) {
                    "⚡ Яндекс Банк выбран главным на этот месяц из-за категорий кэшбэка. При вводе расходов он будет выбираться автоматически."
                } else {
                    "ВТБ используется для продуктов и остальных покупок. Если в Яндекс Банке появились более выгодные кэшбэки, включите его здесь в 1 тап."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
