package com.example.ui.screens.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.util.CurrencyHelper

@Composable
fun HistoryPeriodSegmentedControl(
    selectedPeriod: HistoryPeriodType,
    onPeriodSelected: (HistoryPeriodType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .testTag("history_period_segmented_control"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryPeriodType.values().forEach { period ->
                val isSelected = period == selectedPeriod
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "segmented_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "segmented_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onPeriodSelected(period)
                        }
                        .padding(vertical = 8.dp)
                        .testTag("history_segment_${period.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryPeriodSummaryCard(
    periodTitle: String,
    totalCount: Int,
    totalExpense: Double,
    baseCurrency: String,
    isCustomPeriod: Boolean = false,
    onEditCustomRange: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .then(
                if (isCustomPeriod && onEditCustomRange != null) {
                    Modifier.clickable { onEditCustomRange() }
                } else Modifier
            )
            .testTag("history_period_summary_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Line 1: Period title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = periodTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isCustomPeriod && onEditCustomRange != null) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Изменить период",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Line 2: Operations count (with Russian pluralization)
            Text(
                text = HistoryPeriodHelper.formatOperationsCount(totalCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Line 3: Expense sum
            val expenseText = if (totalExpense > 0.0) {
                CurrencyHelper.formatAmount(-totalExpense, baseCurrency)
            } else {
                CurrencyHelper.formatAmount(0.0, baseCurrency)
            }

            Text(
                text = expenseText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (totalExpense > 0.0) ExpenseRed else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDateRangePickerDialog(
    initialStartDate: Long?,
    initialEndDate: Long?,
    onDismissRequest: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate,
        initialSelectedEndDateMillis = initialEndDate
    )
    val confirmEnabled = remember {
        derivedStateOf {
            dateRangePickerState.selectedStartDateMillis != null &&
            dateRangePickerState.selectedEndDateMillis != null
        }
    }

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val s = dateRangePickerState.selectedStartDateMillis
                    val e = dateRangePickerState.selectedEndDateMillis
                    if (s != null && e != null) {
                        val (localStart, localEnd) = HistoryPeriodHelper.utcMillisToLocalDayRange(s, e)
                        onConfirm(localStart, localEnd)
                    }
                },
                enabled = confirmEnabled.value
            ) {
                Text("Выбрать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Отмена")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Выберите период",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}
