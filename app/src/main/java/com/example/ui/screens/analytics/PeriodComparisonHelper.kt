package com.example.ui.screens.analytics

import com.example.data.entity.AccountEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.DebtEntity
import com.example.data.entity.TransactionEntity
import com.example.service.PaydayPeriod
import com.example.service.UserFinancePreferences
import com.example.ui.util.CurrencyHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CategoryComparisonItem(
    val category: CategoryEntity,
    val currentAmount: Double,
    val prevAmount: Double,
    val diff: Double, // currentAmount - prevAmount
    val percentChange: Int? // null if prevAmount == 0.0
)

data class PeriodComparisonData(
    val isAvailable: Boolean = true, // false for ALL_TIME
    val hasEnoughData: Boolean = false, // true if prevExpense > 0.0
    val currentExpense: Double = 0.0,
    val prevExpense: Double = 0.0,
    val diff: Double = 0.0,
    val percentChange: Int? = null,
    val isIncreased: Boolean = false,
    val isLfl: Boolean = false,
    val daysCompared: Int = 0,
    val currentPeriodLabel: String = "",
    val prevPeriodLabel: String = "",
    val categoryDeltas: List<CategoryComparisonItem> = emptyList(),
    val currentTransactions: List<TransactionEntity> = emptyList(),
    val prevTransactions: List<TransactionEntity> = emptyList()
) {
    val topIncreases: List<CategoryComparisonItem>
        get() = categoryDeltas.filter { it.diff > 0.01 }.sortedByDescending { it.diff }

    val topSavings: List<CategoryComparisonItem>
        get() = categoryDeltas.filter { it.diff < -0.01 }.sortedBy { it.diff } // largest negative first
}

object PeriodComparisonHelper {

    /**
     * Calculates comparative analytics between the active period and its preceding comparable period.
     */
    fun calculate(
        periodType: AnalyticsPeriodType,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categoriesMap: Map<Long, CategoryEntity>,
        debtsMap: Map<Long, DebtEntity>,
        baseCurrency: String,
        activePaydayPeriod: PaydayPeriod,
        targetPayday: Int,
        preferences: UserFinancePreferences? = null,
        now: Long = System.currentTimeMillis()
    ): PeriodComparisonData {
        if (periodType == AnalyticsPeriodType.ALL_TIME) {
            return PeriodComparisonData(
                isAvailable = false,
                currentPeriodLabel = "Все время"
            )
        }

        val (curStart, curEnd, curLabel, prevStart, prevEnd, prevLabel, isLflCandidate) =
            resolvePeriodBoundaries(
                periodType = periodType,
                activePaydayPeriod = activePaydayPeriod,
                targetPayday = targetPayday,
                preferences = preferences,
                now = now
            )

        val isLfl = isLflCandidate && (now in curStart..curEnd)
        val curEffectiveEnd = if (isLfl) min(curEnd, now) else curEnd

        val (prevEffectiveEnd, daysCompared) = if (isLfl) {
            val elapsedMs = max(0L, now - curStart)
            val lflEnd = min(prevStart + elapsedMs, prevEnd)
            val days = max(1, ((elapsedMs / 86_400_000L) + 1).toInt())
            Pair(lflEnd, days)
        } else {
            val totalPeriodMs = max(1L, curEnd - curStart)
            val days = max(1, ((totalPeriodMs / 86_400_000L) + 1).toInt())
            Pair(prevEnd, days)
        }

        // Filter and calculate current period
        val (curExpense, curSpendMap, curTxs) = calculateExpensesAndSpendings(
            startTime = curStart,
            endTime = curEffectiveEnd,
            transactions = transactions,
            accounts = accounts,
            categoriesMap = categoriesMap,
            debtsMap = debtsMap,
            baseCurrency = baseCurrency
        )

        // Filter and calculate previous period
        val (prevExpense, prevSpendMap, prevTxs) = calculateExpensesAndSpendings(
            startTime = prevStart,
            endTime = prevEffectiveEnd,
            transactions = transactions,
            accounts = accounts,
            categoriesMap = categoriesMap,
            debtsMap = debtsMap,
            baseCurrency = baseCurrency
        )

        // Determine if we have enough historical data
        val hasEnoughData = prevExpense > 0.001

        val diff = if (hasEnoughData) curExpense - prevExpense else 0.0
        val percentChange = if (hasEnoughData && prevExpense > 0.0) {
            ((diff / prevExpense) * 100).toInt()
        } else {
            null
        }
        val isIncreased = diff > 0.0

        // Calculate category deltas
        val allCatKeys = (curSpendMap.keys + prevSpendMap.keys).distinctBy { it.id }
        val categoryDeltas = allCatKeys.map { cat ->
            val curAmt = curSpendMap.entries.firstOrNull { it.key.id == cat.id }?.value ?: 0.0
            val prvAmt = prevSpendMap.entries.firstOrNull { it.key.id == cat.id }?.value ?: 0.0
            val catDiff = curAmt - prvAmt
            val catPct = if (prvAmt > 0.0) {
                ((catDiff / prvAmt) * 100).toInt()
            } else {
                null
            }
            CategoryComparisonItem(
                category = cat,
                currentAmount = curAmt,
                prevAmount = prvAmt,
                diff = catDiff,
                percentChange = catPct
            )
        }.sortedByDescending { abs(it.diff) }

        return PeriodComparisonData(
            isAvailable = true,
            hasEnoughData = hasEnoughData,
            currentExpense = curExpense,
            prevExpense = prevExpense,
            diff = diff,
            percentChange = percentChange,
            isIncreased = isIncreased,
            isLfl = isLfl,
            daysCompared = daysCompared,
            currentPeriodLabel = curLabel,
            prevPeriodLabel = prevLabel,
            categoryDeltas = categoryDeltas,
            currentTransactions = curTxs,
            prevTransactions = prevTxs
        )
    }

    private data class PeriodBoundaries(
        val curStart: Long,
        val curEnd: Long,
        val curLabel: String,
        val prevStart: Long,
        val prevEnd: Long,
        val prevLabel: String,
        val isLflCandidate: Boolean
    )

    private fun resolvePeriodBoundaries(
        periodType: AnalyticsPeriodType,
        activePaydayPeriod: PaydayPeriod,
        targetPayday: Int,
        preferences: UserFinancePreferences?,
        now: Long
    ): PeriodBoundaries {
        val monthFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))

        return when (periodType) {
            AnalyticsPeriodType.PAYDAY -> {
                val curStart = activePaydayPeriod.startTime
                val curEnd = activePaydayPeriod.endTime
                val curLabel = activePaydayPeriod.periodLabel.ifEmpty { "Текущий цикл зарплаты" }

                // Calculate previous payday cycle by stepping 1 second before curStart
                val prevTargetTimestamp = curStart - 1000L
                val prevPeriod = preferences?.calculatePaydayPeriod(targetPayday, prevTargetTimestamp)
                    ?: fallbackPaydayPeriod(targetPayday, prevTargetTimestamp)

                PeriodBoundaries(
                    curStart = curStart,
                    curEnd = curEnd,
                    curLabel = curLabel,
                    prevStart = prevPeriod.startTime,
                    prevEnd = prevPeriod.endTime,
                    prevLabel = prevPeriod.periodLabel.ifEmpty { "Прошлый цикл" },
                    isLflCandidate = true
                )
            }
            AnalyticsPeriodType.CURRENT_MONTH -> {
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                val curLabel = monthFormat.format(cal.time).replaceFirstChar { it.uppercase() }

                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val curStart = cal.timeInMillis

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val curEnd = cal.timeInMillis

                // Previous calendar month
                val prevCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.MONTH, -1)
                }
                val prevLabel = monthFormat.format(prevCal.time).replaceFirstChar { it.uppercase() }

                prevCal.set(Calendar.DAY_OF_MONTH, 1)
                prevCal.set(Calendar.HOUR_OF_DAY, 0)
                prevCal.set(Calendar.MINUTE, 0)
                prevCal.set(Calendar.SECOND, 0)
                prevCal.set(Calendar.MILLISECOND, 0)
                val prevStart = prevCal.timeInMillis

                val maxDayPrev = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                prevCal.set(Calendar.DAY_OF_MONTH, maxDayPrev)
                prevCal.set(Calendar.HOUR_OF_DAY, 23)
                prevCal.set(Calendar.MINUTE, 59)
                prevCal.set(Calendar.SECOND, 59)
                prevCal.set(Calendar.MILLISECOND, 999)
                val prevEnd = prevCal.timeInMillis

                PeriodBoundaries(
                    curStart = curStart,
                    curEnd = curEnd,
                    curLabel = curLabel,
                    prevStart = prevStart,
                    prevEnd = prevEnd,
                    prevLabel = prevLabel,
                    isLflCandidate = true
                )
            }
            AnalyticsPeriodType.PREV_MONTH -> {
                // Current selection is "Previous Month" (1 month ago)
                val curCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.MONTH, -1)
                }
                val curLabel = monthFormat.format(curCal.time).replaceFirstChar { it.uppercase() }

                curCal.set(Calendar.DAY_OF_MONTH, 1)
                curCal.set(Calendar.HOUR_OF_DAY, 0)
                curCal.set(Calendar.MINUTE, 0)
                curCal.set(Calendar.SECOND, 0)
                curCal.set(Calendar.MILLISECOND, 0)
                val curStart = curCal.timeInMillis

                val maxDayCur = curCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                curCal.set(Calendar.DAY_OF_MONTH, maxDayCur)
                curCal.set(Calendar.HOUR_OF_DAY, 23)
                curCal.set(Calendar.MINUTE, 59)
                curCal.set(Calendar.SECOND, 59)
                curCal.set(Calendar.MILLISECOND, 999)
                val curEnd = curCal.timeInMillis

                // Comparative period is 2 months ago
                val prevCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.MONTH, -2)
                }
                val prevLabel = monthFormat.format(prevCal.time).replaceFirstChar { it.uppercase() }

                prevCal.set(Calendar.DAY_OF_MONTH, 1)
                prevCal.set(Calendar.HOUR_OF_DAY, 0)
                prevCal.set(Calendar.MINUTE, 0)
                prevCal.set(Calendar.SECOND, 0)
                prevCal.set(Calendar.MILLISECOND, 0)
                val prevStart = prevCal.timeInMillis

                val maxDayPrev = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                prevCal.set(Calendar.DAY_OF_MONTH, maxDayPrev)
                prevCal.set(Calendar.HOUR_OF_DAY, 23)
                prevCal.set(Calendar.MINUTE, 59)
                prevCal.set(Calendar.SECOND, 59)
                prevCal.set(Calendar.MILLISECOND, 999)
                val prevEnd = prevCal.timeInMillis

                PeriodBoundaries(
                    curStart = curStart,
                    curEnd = curEnd,
                    curLabel = curLabel,
                    prevStart = prevStart,
                    prevEnd = prevEnd,
                    prevLabel = prevLabel,
                    isLflCandidate = false // Both are fully completed past months
                )
            }
            AnalyticsPeriodType.LAST_30_DAYS -> {
                val thirtyDaysMs = 30L * 86_400_000L
                val curStart = now - thirtyDaysMs
                val curEnd = now
                val curLabel = "Последние 30 дней"

                val prevStart = now - (2 * thirtyDaysMs)
                val prevEnd = now - thirtyDaysMs
                val prevLabel = "Предыдущие 30 дней"

                PeriodBoundaries(
                    curStart = curStart,
                    curEnd = curEnd,
                    curLabel = curLabel,
                    prevStart = prevStart,
                    prevEnd = prevEnd,
                    prevLabel = prevLabel,
                    isLflCandidate = false // Comparing two identical 30-day windows
                )
            }
            AnalyticsPeriodType.ALL_TIME -> {
                PeriodBoundaries(0L, Long.MAX_VALUE, "Все время", 0L, 0L, "", false)
            }
        }
    }

    private data class CalculationResult(
        val totalExpense: Double,
        val spendMap: Map<CategoryEntity, Double>,
        val expenseTransactions: List<TransactionEntity>
    )

    private fun calculateExpensesAndSpendings(
        startTime: Long,
        endTime: Long,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categoriesMap: Map<Long, CategoryEntity>,
        debtsMap: Map<Long, DebtEntity>,
        baseCurrency: String
    ): CalculationResult {
        val filteredTxs = transactions.filter {
            it.timestamp in startTime..endTime && !it.excludeFromStats
        }

        var totalExpense = 0.0
        val spendMap = mutableMapOf<CategoryEntity, Double>()
        val expenseTransactions = mutableListOf<TransactionEntity>()

        for (tx in filteredTxs) {
            val acc = accounts.find { it.id == tx.accountId }
            val toAcc = accounts.find { it.id == tx.toAccountId }
            val accInAnalytics = acc?.includeInAnalytics ?: true
            val toAccInAnalytics = toAcc?.includeInAnalytics ?: true

            val isExpense = tx.type == "EXPENSE" && accInAnalytics
            val isTransferOut = tx.type == "TRANSFER" && accInAnalytics && !toAccInAnalytics

            if (isExpense || isTransferOut) {
                val convertedAmount = CurrencyHelper.convert(
                    tx.amount,
                    acc?.currency ?: baseCurrency,
                    baseCurrency
                )

                totalExpense += convertedAmount
                expenseTransactions.add(tx)

                val cat = when {
                    tx.debtId != null -> {
                        val debt = debtsMap[tx.debtId]
                        val debtName = debt?.personName ?: "Неизвестный долг"
                        CategoryEntity(
                            id = -(tx.debtId ?: 1L) - 100000L,
                            name = "Долг: $debtName",
                            iconName = "money_off",
                            colorHex = "#F44336",
                            type = "EXPENSE"
                        )
                    }
                    tx.categoryId != null -> {
                        categoriesMap[tx.categoryId] ?: CategoryEntity(
                            id = tx.categoryId ?: -1L,
                            name = "Без категории",
                            iconName = "more_horiz",
                            colorHex = "#94A3B8",
                            type = "EXPENSE"
                        )
                    }
                    isTransferOut -> {
                        CategoryEntity(
                            id = -2L,
                            name = "Переводы",
                            iconName = "swap_horiz",
                            colorHex = "#9E9E9E",
                            type = "EXPENSE"
                        )
                    }
                    else -> {
                        CategoryEntity(
                            id = -1L,
                            name = "Без категории",
                            iconName = "more_horiz",
                            colorHex = "#94A3B8",
                            type = "EXPENSE"
                        )
                    }
                }

                spendMap[cat] = (spendMap[cat] ?: 0.0) + convertedAmount
            }
        }

        return CalculationResult(totalExpense, spendMap, expenseTransactions)
    }

    private fun fallbackPaydayPeriod(targetPayday: Int, timestamp: Long): PaydayPeriod {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startDay = min(targetPayday, maxDays)

        val startCal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            if (day < targetPayday) {
                add(Calendar.MONTH, -1)
            }
            set(Calendar.DAY_OF_MONTH, min(targetPayday, getActualMaximum(Calendar.DAY_OF_MONTH)))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }

        val labelFormat = SimpleDateFormat("d MMM", Locale("ru"))
        val label = "${labelFormat.format(startCal.time)} — ${labelFormat.format(endCal.time)}"
        return PaydayPeriod(
            startTime = startCal.timeInMillis,
            endTime = endCal.timeInMillis,
            periodLabel = label,
            daysUntilPayday = 0,
            dayOfCycle = 1,
            totalDaysInCycle = 30
        )
    }
}
