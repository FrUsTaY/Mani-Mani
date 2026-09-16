package com.example.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.util.CurrencyHelper
import com.example.ui.util.IconHelper
import com.example.ui.viewmodel.CategorySpending
import kotlin.math.max

/**
 * Custom forecast projection chart matching Zen-money's Plans graph.
 * Displays:
 * - Start expense point on left (e.g. 4 079 ₽)
 * - Solid charcoal line up to current date (e.g. 13 сен)
 * - Upper dashed green forecast ceiling curve (e.g. 149 680 ₽)
 * - Lower dashed blue planned expenses trajectory curve
 */
@Composable
fun ZenForecastChart(
    startAmount: Double,
    currentExpense: Double,
    remainingPlanned: Double,
    forecastCeiling: Double,
    dayOfCycle: Int,
    totalDaysInCycle: Int,
    currency: String,
    todayLabel: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .testTag("zen_forecast_chart")
    ) {
        val charcoalColor = Color(0xFF2C3437)
        val forecastGreen = Color(0xFF34A853)
        val planBlue = Color(0xFF1E88E5)
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val leftPadding = 48.dp.toPx()
            val rightPadding = 75.dp.toPx()
            val topPadding = 24.dp.toPx()
            val bottomPadding = 32.dp.toPx()

            val chartWidth = width - leftPadding - rightPadding
            val chartHeight = height - topPadding - bottomPadding

            // Draw subtle horizontal grid guidelines
            for (i in 0..2) {
                val y = topPadding + (chartHeight / 2) * i
                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, y),
                    end = Offset(width - rightPadding, y),
                    strokeWidth = 1f
                )
            }

            val cycleRatio = (dayOfCycle.toFloat() / max(totalDaysInCycle, 1)).coerceIn(0.08f, 0.92f)
            val currentX = leftPadding + chartWidth * cycleRatio

            // Vertical coordinates
            val startY = height - bottomPadding - 6f
            val currentY = topPadding + chartHeight * 0.42f
            val ceilingY = topPadding + 6f
            val planTargetY = topPadding + chartHeight * 0.28f

            // 1. Solid actual expense curve (Day 1 -> Today)
            val solidPath = Path().apply {
                moveTo(leftPadding, startY)
                val midX = (leftPadding + currentX) / 2
                cubicTo(
                    midX, startY,
                    midX, currentY,
                    currentX, currentY
                )
            }
            drawPath(
                path = solidPath,
                color = charcoalColor,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Dashed Upper Green Forecast Curve (Today -> End of cycle)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f), 0f)
            val upperDashedPath = Path().apply {
                moveTo(currentX, currentY)
                val endX = leftPadding + chartWidth
                val midX = (currentX + endX) / 2
                cubicTo(
                    midX, currentY - 8f,
                    midX, ceilingY + 8f,
                    endX, ceilingY
                )
            }
            drawPath(
                path = upperDashedPath,
                color = forecastGreen,
                style = Stroke(width = 3.dp.toPx(), pathEffect = dashEffect, cap = StrokeCap.Round)
            )

            // 3. Dashed Lower Blue Plan Curve (Today -> End of cycle)
            val lowerDashedPath = Path().apply {
                moveTo(currentX, currentY)
                val endX = leftPadding + chartWidth
                val midX = (currentX + endX) / 2
                cubicTo(
                    midX, currentY,
                    midX, planTargetY,
                    endX, planTargetY
                )
            }
            drawPath(
                path = lowerDashedPath,
                color = planBlue,
                style = Stroke(width = 3.dp.toPx(), pathEffect = dashEffect, cap = StrokeCap.Round)
            )

            // 4. Pivot Dots at Current Date
            drawCircle(
                color = forecastGreen,
                radius = 5.dp.toPx(),
                center = Offset(currentX, currentY - 2.dp.toPx())
            )
            drawCircle(
                color = charcoalColor,
                radius = 4.5.dp.toPx(),
                center = Offset(currentX, currentY + 4.dp.toPx())
            )
        }

        // Labels overlaid with Compose
        // Start Amount Label
        Text(
            text = CurrencyHelper.formatAmount(startAmount, currency),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 24.dp)
        )

        // Today Date Label
        Text(
            text = todayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )

        // Ceiling Forecast Amount Label
        Text(
            text = CurrencyHelper.formatAmount(forecastCeiling, currency),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 12.dp)
        )
    }
}

/**
 * Card 1: "Планы" (matching Screenshot 2 from Zen-money)
 */
@Composable
fun ZenPlansCard(
    currentExpense: Double,
    remainingPlanned: Double,
    freeMoney: Double,
    forecastCeiling: Double,
    dayOfCycle: Int,
    totalDaysInCycle: Int,
    currency: String,
    todayLabel: String,
    onOpenPlans: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenPlans() }
            .testTag("zen_plans_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: "Планы" >
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Планы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Перейти к планам",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two main metrics: Expenses from start of month & Remaining planned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Расходы с начала месяца",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyHelper.formatAmount(currentExpense, currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "ещё в планах",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyHelper.formatAmount(remainingPlanned, currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E88E5),
                        fontSize = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Forecast Chart
            val baselineStart = (currentExpense * 0.15).coerceAtLeast(100.0)
            val safeCeiling = if (forecastCeiling > 0) forecastCeiling else (currentExpense + remainingPlanned * 1.3).coerceAtLeast(currentExpense * 1.2)

            ZenForecastChart(
                startAmount = baselineStart,
                currentExpense = currentExpense,
                remainingPlanned = remainingPlanned,
                forecastCeiling = safeCeiling,
                dayOfCycle = dayOfCycle,
                totalDaysInCycle = totalDaysInCycle,
                currency = currency,
                todayLabel = todayLabel
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Free money pill banner (Light mint green with green dot)
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("zen_free_money_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF81C784).copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32))
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Свободно на конец месяца ${CurrencyHelper.formatAmount(freeMoney, currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
        }
    }
}

/**
 * Card 2: "Доходы vs Расходы" (matching Screenshot 2 from Zen-money)
 */
@Composable
fun ZenIncomeVsExpenseCard(
    income: Double,
    expense: Double,
    periodLabel: String,
    currency: String,
    onOpenDetails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val delta = income - expense
    val percentage = if (income > 0) ((expense / income) * 100).toInt() else if (expense > 0) 100 else 0
    val maxVal = max(income, max(expense, Math.abs(delta))).coerceAtLeast(1.0)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDetails() }
            .testTag("zen_income_vs_expense_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: "Доходы vs Расходы" >
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Доходы vs Расходы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Подробнее о доходах и расходах",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Percentage & Period caption
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp
            )
            Text(
                text = "Доля расходов за $periodLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Row 1: Доходы (Green bar)
            ComparativeBarRow(
                label = "Доходы",
                amount = income,
                ratio = (income / maxVal).toFloat(),
                barColor = Color(0xFF34A853),
                currency = currency
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Расходы (Charcoal bar)
            ComparativeBarRow(
                label = "Расходы",
                amount = expense,
                ratio = (expense / maxVal).toFloat(),
                barColor = Color(0xFF2C3437),
                currency = currency
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Остаток (Orange-red or green)
            val deltaColor = if (delta >= 0) Color(0xFF34A853) else Color(0xFFFF5722)
            ComparativeBarRow(
                label = "Остаток",
                amount = delta,
                ratio = (Math.abs(delta) / maxVal).toFloat(),
                barColor = deltaColor,
                currency = currency,
                showSign = true
            )
        }
    }
}

@Composable
private fun ComparativeBarRow(
    label: String,
    amount: Double,
    ratio: Float,
    barColor: Color,
    currency: String,
    showSign: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio.coerceIn(0.04f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = CurrencyHelper.formatAmount(amount, currency, showPlus = showSign && amount > 0),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 80.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/**
 * Top AI Query Bar with red friendly mascot (Zen-bot)
 */
@Composable
fun ZenAiQueryBar(
    onQuestionSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { showDialog = true }
            .testTag("zen_ai_query_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red Zen-like avatar / mascot icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFEBEE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "Дзен-помощник",
                tint = Color(0xFFE53935),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Input Pill box
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Задайте вопрос про аналитику",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().testTag("zen_ai_query_dialog")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Вопрос по аналитике",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = { Text("Например: На что ушло больше всего денег?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("zen_ai_text_input"),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (queryText.isNotBlank()) {
                                    onQuestionSubmitted(queryText)
                                    showDialog = false
                                }
                            }
                        ),
                        trailingIcon = {
                            if (queryText.isNotBlank()) {
                                IconButton(onClick = {
                                    onQuestionSubmitted(queryText)
                                    showDialog = false
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Отправить",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Быстрые вопросы:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val quickQuestions = listOf(
                        "Хватит ли мне денег до зарплаты?",
                        "Сколько я трачу на продукты?",
                        "Сравни траты с прошлым месяцем",
                        "Где я могу сэкономить?"
                    )

                    quickQuestions.forEach { question ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    onQuestionSubmitted(question)
                                    showDialog = false
                                }
                        ) {
                            Text(
                                text = question,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (queryText.isNotBlank()) {
                                    onQuestionSubmitted(queryText)
                                    showDialog = false
                                }
                            },
                            enabled = queryText.isNotBlank()
                        ) {
                            Text("Спросить ИИ")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card 3: "Анализ расходов по категориям" (Donut Chart & Breakdown)
 */
@Composable
fun ZenCategoryAnalysisCard(
    spendings: List<CategorySpending>,
    totalExpense: Double,
    currency: String,
    onOpenDetails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDetails() }
            .testTag("zen_category_analysis_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Анализ расходов по категориям",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Помогает понять, куда уходят деньги",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (spendings.isNotEmpty() && totalExpense > 0) {
                // Donut Chart
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Canvas(modifier = Modifier.size(160.dp)) {
                        var currentAngle = -90f
                        val strokeWidth = 26.dp.toPx()

                        spendings.forEach { item ->
                            val sweep = item.percentage * 360f
                            val color = IconHelper.parseColor(item.category.colorHex)
                            drawArc(
                                color = color,
                                startAngle = currentAngle,
                                sweepAngle = sweep.coerceAtLeast(1.5f),
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            currentAngle += sweep
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Всего",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyHelper.formatAmount(totalExpense, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Top Category list
                spendings.take(5).forEach { item ->
                    val catColor = IconHelper.parseColor(item.category.colorHex)
                    val percentInt = (item.percentage * 100).toInt()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(catColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIconByName(item.category.iconName),
                                contentDescription = item.category.name,
                                tint = catColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = CurrencyHelper.formatAmount(item.totalAmount, currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { item.percentage.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = catColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$percentInt%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            } else {
                Text(
                    text = "Пока нет данных о тратах за выбранный период",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Card 4: "Сравнение периодов" (from Zen-money "Что внутри")
 */
@Composable
fun ZenPeriodComparisonCard(
    currentExpense: Double,
    prevExpense: Double,
    currency: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val diff = currentExpense - prevExpense
    val percentChange = if (prevExpense > 0) ((diff / prevExpense) * 100).toInt() else 0
    val isIncreased = diff > 0

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("zen_period_comparison_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Сравнение периодов",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Показывает разницу в расходах и причины изменений",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isIncreased) "+$percentChange%" else "$percentChange%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isIncreased) ExpenseRed else IncomeGreen
                    )
                    Text(
                        text = if (isIncreased) "Траты выросли на ${CurrencyHelper.formatAmount(Math.abs(diff), currency)}"
                        else "Экономия ${CurrencyHelper.formatAmount(Math.abs(diff), currency)} по сравнению с прошлым циклом",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isIncreased) ExpenseRed.copy(alpha = 0.12f) else IncomeGreen.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isIncreased) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isIncreased) ExpenseRed else IncomeGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isIncreased) "Выше нормы" else "Отличный темп",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncreased) ExpenseRed else IncomeGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Comparative side-by-side bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Текущий период", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyHelper.formatAmount(currentExpense, currency), fontWeight = FontWeight.Bold)
                }
                Text("vs", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Прошлый период", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyHelper.formatAmount(prevExpense, currency), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Card 5: "Динамика накоплений" (Savings & Net Worth)
 */
@Composable
fun ZenSavingsDynamicsCard(
    totalBalance: Double,
    savingsAmount: Double,
    currency: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("zen_savings_dynamics_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Динамика накоплений",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Показывает тренд роста накоплений и капитал",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Общий капитал счетов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyHelper.formatAmount(totalBalance, currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("В копилках и целях", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyHelper.formatAmount(savingsAmount, currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E88E5)
                    )
                }
            }
        }
    }
}

/**
 * Card 6: "Свободные деньги" (from Zen-money "Что внутри")
 */
@Composable
fun ZenFreeMoneyCard(
    freeMoney: Double,
    daysUntilPayday: Int,
    currency: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val days = max(daysUntilPayday, 1)
    val dailyBudget = freeMoney / days

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("zen_free_money_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Свободные деньги",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Безопасный лимит расходов до конца месяца",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Безопасно в день",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${CurrencyHelper.formatAmount(dailyBudget, currency)} / день",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2E7D32)
                    )
                }

                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Дней до зарплаты",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "$days дн.",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact View List for Zen-money reports
 */
@Composable
fun ZenCompactReportsList(
    currentExpense: Double,
    remainingPlanned: Double,
    income: Double,
    freeMoney: Double,
    daysUntilPayday: Int,
    currency: String,
    onOpenPlans: () -> Unit,
    onOpenIncomeVsExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("Планы", "Расходы ${CurrencyHelper.formatAmount(currentExpense, currency)} • В планах ${CurrencyHelper.formatAmount(remainingPlanned, currency)}", Icons.Default.TrackChanges),
        Triple("Доходы vs Расходы", "Доходы ${CurrencyHelper.formatAmount(income, currency)} • Расходы ${CurrencyHelper.formatAmount(currentExpense, currency)}", Icons.Default.CompareArrows),
        Triple("Свободные деньги", "Свободно на конец месяца ${CurrencyHelper.formatAmount(freeMoney, currency)}", Icons.Default.AccountBalanceWallet),
        Triple("Динамика накоплений", "Контроль капитала и сбережений", Icons.Default.TrendingUp),
        Triple("Сравнение периодов", "Анализ динамики и структуры изменений", Icons.Default.Assessment)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            items.forEachIndexed { index, (title, desc, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (index == 0) onOpenPlans()
                            else onOpenIncomeVsExpense()
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index < items.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
fun ZenFreeFeatureBanner(
    onOpenAiAudit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("zen_free_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Аналитика Дзен-мани без подписок",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Все отчёты, прогнозы и ИИ-инсайты бесплатны",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onOpenAiAudit,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ИИ аудит", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
