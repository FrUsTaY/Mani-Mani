package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AccountEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.DateHelper
import com.example.ui.util.IconHelper

@Composable
fun TransactionItemCard(
    transaction: TransactionEntity,
    accountsMap: Map<Long, AccountEntity>,
    categoriesMap: Map<Long, CategoryEntity>,
    goalsMap: Map<Long, com.example.data.entity.GoalEntity> = emptyMap(),
    debtsMap: Map<Long, com.example.data.entity.DebtEntity> = emptyMap(),
    onDelete: (TransactionEntity) -> Unit,
    onClick: ((TransactionEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val account = accountsMap[transaction.accountId]
    val toAccount = transaction.toAccountId?.let { accountsMap[it] }
    val category = transaction.categoryId?.let { categoriesMap[it] }
    val goal = transaction.goalId?.let { goalsMap[it] }
    val debt = transaction.debtId?.let { debtsMap[it] }
    val currency = account?.currency ?: "RUB"

    val (icon, badgeColor, title, subtitle) = when (transaction.type) {
        "EXPENSE" -> {
            val iconVec = if (debt != null) Icons.Default.MoneyOff else category?.let { IconHelper.getIconByName(it.iconName) } ?: IconHelper.getIconByName("shopping_cart")
            val color = if (debt != null) MaterialTheme.colorScheme.error else category?.let { IconHelper.parseColor(it.colorHex) } ?: ExpenseRed
            val sub = buildString {
                append(account?.name ?: "Счёт")
                if (transaction.note.isNotBlank()) append(" • ${transaction.note}")
            }
            val dispTitle = debt?.let { "Долг: ${it.personName}" } ?: category?.name ?: "Расход"
            Quad(iconVec, color, dispTitle, sub)
        }
        "INCOME" -> {
            val iconVec = if (debt != null) Icons.Default.MoneyOff else category?.let { IconHelper.getIconByName(it.iconName) } ?: IconHelper.getIconByName("payments")
            val color = if (debt != null) MaterialTheme.colorScheme.error else category?.let { IconHelper.parseColor(it.colorHex) } ?: IncomeGreen
            val sub = buildString {
                append(account?.name ?: "Счёт")
                if (transaction.note.isNotBlank()) append(" • ${transaction.note}")
            }
            val dispTitle = debt?.let { "Возврат долга: ${it.personName}" } ?: category?.name ?: "Доход"
            Quad(iconVec, color, dispTitle, sub)
        }
        else -> {
            val iconVec = if (goal != null) IconHelper.getIconByName(goal.iconName) else Icons.AutoMirrored.Filled.CompareArrows
            val color = if (goal != null) IconHelper.parseColor(goal.colorHex) else TransferBlue
            val fromName = account?.name ?: "Счёт"
            val toName = if (goal != null) "Копилка: ${goal.name}" else toAccount?.name ?: "Счёт"
            val dispTitle = if (goal != null) "В копилку" else "Перевод"
            Quad(iconVec, color, dispTitle, "$fromName → $toName")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick(transaction) }
                } else Modifier
            )
            .testTag("transaction_item_${transaction.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category / Type Icon badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateHelper.formatDate(transaction.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                val amountText = when (transaction.type) {
                    "EXPENSE" -> CurrencyHelper.formatAmount(-transaction.amount, currency)
                    "INCOME" -> CurrencyHelper.formatAmount(transaction.amount, currency, showPlus = true)
                    else -> CurrencyHelper.formatAmount(transaction.amount, currency)
                }

                val amountColor = when (transaction.type) {
                    "EXPENSE" -> ExpenseRed
                    "INCOME" -> IncomeGreen
                    else -> TransferBlue
                }

                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                IconButton(
                    onClick = { onDelete(transaction) },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_transaction_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить операцию",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
