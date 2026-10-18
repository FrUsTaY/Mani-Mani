package com.example

import com.example.data.entity.AccountEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.service.PaydayPeriod
import com.example.ui.screens.analytics.AnalyticsPeriodType
import com.example.ui.screens.analytics.PeriodComparisonHelper
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PeriodComparisonHelperTest {

    private val rubAccount = AccountEntity(
        id = 1L,
        name = "Основной",
        type = "DEBIT_CARD",
        balance = 50000.0,
        currency = "RUB",
        includeInAnalytics = true
    )

    private val catFood = CategoryEntity(
        id = 10L,
        name = "Продукты",
        iconName = "restaurant",
        colorHex = "#FF5722",
        type = "EXPENSE"
    )

    private val catCafe = CategoryEntity(
        id = 20L,
        name = "Кафе",
        iconName = "local_cafe",
        colorHex = "#9C27B0",
        type = "EXPENSE"
    )

    @Test
    fun `test ALL_TIME period is unavailable for comparison`() {
        val result = PeriodComparisonHelper.calculate(
            periodType = AnalyticsPeriodType.ALL_TIME,
            transactions = emptyList(),
            accounts = listOf(rubAccount),
            categoriesMap = mapOf(catFood.id to catFood),
            debtsMap = emptyMap(),
            baseCurrency = "RUB",
            activePaydayPeriod = PaydayPeriod(0L, 0L, "10 сен — 9 окт", 0, 1, 30),
            targetPayday = 10
        )

        assertFalse(result.isAvailable)
    }

    @Test
    fun `test empty previous period returns hasEnoughData false without fake multipliers`() {
        val now = System.currentTimeMillis()
        val curStart = now - 5L * 86_400_000L
        val curEnd = now + 25L * 86_400_000L

        val txCurrent = TransactionEntity(
            id = 100L,
            accountId = 1L,
            amount = 5000.0,
            type = "EXPENSE",
            categoryId = catFood.id,
            timestamp = now - 1000L
        )

        val result = PeriodComparisonHelper.calculate(
            periodType = AnalyticsPeriodType.PAYDAY,
            transactions = listOf(txCurrent),
            accounts = listOf(rubAccount),
            categoriesMap = mapOf(catFood.id to catFood),
            debtsMap = emptyMap(),
            baseCurrency = "RUB",
            activePaydayPeriod = PaydayPeriod(curStart, curEnd, "10 сен — 9 окт", 25, 5, 30),
            targetPayday = 10,
            now = now
        )

        assertTrue(result.isAvailable)
        assertFalse("Should honestly report lack of previous data", result.hasEnoughData)
        assertEquals(5000.0, result.currentExpense, 0.01)
        assertEquals(0.0, result.prevExpense, 0.01)
        assertNull("Percent change must be null when there is no previous data", result.percentChange)
    }

    @Test
    fun `test LFL day-to-day slice in Payday cycle`() {
        // Set fixed date: Sept 14, 2026, 12:00:00 (4.5 days into cycle starting Sept 10)
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 14, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.timeInMillis

        val curStartCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val curStart = curStartCal.timeInMillis

        val curEndCal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 9, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val curEnd = curEndCal.timeInMillis

        // Prior cycle starts Aug 10, 2026
        val prevStartCal = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val prevStart = prevStartCal.timeInMillis

        val transactions = listOf(
            // Current period: 3000 on Food (Sept 12)
            TransactionEntity(
                id = 1L,
                accountId = 1L,
                amount = 3000.0,
                type = "EXPENSE",
                categoryId = catFood.id,
                timestamp = curStart + 2L * 86_400_000L
            ),
            // Prev period within LFL slice (Aug 12 - day 2 of prev cycle): 2000 on Food
            TransactionEntity(
                id = 2L,
                accountId = 1L,
                amount = 2000.0,
                type = "EXPENSE",
                categoryId = catFood.id,
                timestamp = prevStart + 2L * 86_400_000L
            ),
            // Prev period OUTSIDE LFL slice (Aug 25 - day 15 of prev cycle): 10000 on Food
            // This should NOT be counted in LFL comparison!
            TransactionEntity(
                id = 3L,
                accountId = 1L,
                amount = 10000.0,
                type = "EXPENSE",
                categoryId = catFood.id,
                timestamp = prevStart + 15L * 86_400_000L
            )
        )

        val result = PeriodComparisonHelper.calculate(
            periodType = AnalyticsPeriodType.PAYDAY,
            transactions = transactions,
            accounts = listOf(rubAccount),
            categoriesMap = mapOf(catFood.id to catFood),
            debtsMap = emptyMap(),
            baseCurrency = "RUB",
            activePaydayPeriod = PaydayPeriod(curStart, curEnd, "10 сен — 9 окт", 25, 5, 30),
            targetPayday = 10,
            now = now
        )

        assertTrue(result.isAvailable)
        assertTrue(result.hasEnoughData)
        assertTrue("Must be LFL comparison", result.isLfl)
        assertEquals(3000.0, result.currentExpense, 0.01)
        // prevExpense must be 2000.0, NOT 12000.0!
        assertEquals(2000.0, result.prevExpense, 0.01)
        assertEquals(1000.0, result.diff, 0.01)
        assertEquals(50, result.percentChange)
        assertTrue(result.isIncreased)
    }

    @Test
    fun `test category deltas identification of increases and savings`() {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        // LAST_30_DAYS:
        // Current: [now - 30 days .. now]
        // Previous: [now - 60 days .. now - 30 days]
        val curTimestamp = now - 5L * dayMs     // 5 days ago (inside current window)
        val prevTimestamp = now - 45L * dayMs   // 45 days ago (inside previous window)

        val transactions = listOf(
            // Food: grew from 1000 to 4000 (+3000)
            TransactionEntity(id = 1L, accountId = 1L, amount = 4000.0, type = "EXPENSE", categoryId = catFood.id, timestamp = curTimestamp),
            TransactionEntity(id = 2L, accountId = 1L, amount = 1000.0, type = "EXPENSE", categoryId = catFood.id, timestamp = prevTimestamp),

            // Cafe: dropped from 5000 to 2000 (-3000)
            TransactionEntity(id = 3L, accountId = 1L, amount = 2000.0, type = "EXPENSE", categoryId = catCafe.id, timestamp = curTimestamp + 1000L),
            TransactionEntity(id = 4L, accountId = 1L, amount = 5000.0, type = "EXPENSE", categoryId = catCafe.id, timestamp = prevTimestamp + 1000L)
        )

        val result = PeriodComparisonHelper.calculate(
            periodType = AnalyticsPeriodType.LAST_30_DAYS,
            transactions = transactions,
            accounts = listOf(rubAccount),
            categoriesMap = mapOf(catFood.id to catFood, catCafe.id to catCafe),
            debtsMap = emptyMap(),
            baseCurrency = "RUB",
            activePaydayPeriod = PaydayPeriod(0L, 0L, "", 0, 0, 30),
            targetPayday = 10,
            now = now
        )

        assertTrue(result.hasEnoughData)
        assertEquals(2, result.categoryDeltas.size)

        val foodDelta = result.categoryDeltas.find { it.category.id == catFood.id }
        assertNotNull(foodDelta)
        assertEquals(3000.0, foodDelta!!.diff, 0.01)
        assertEquals(300, foodDelta.percentChange)

        val cafeDelta = result.categoryDeltas.find { it.category.id == catCafe.id }
        assertNotNull(cafeDelta)
        assertEquals(-3000.0, cafeDelta!!.diff, 0.01)
        assertEquals(-60, cafeDelta.percentChange)

        assertEquals(1, result.topIncreases.size)
        assertEquals(catFood.id, result.topIncreases[0].category.id)

        assertEquals(1, result.topSavings.size)
        assertEquals(catCafe.id, result.topSavings[0].category.id)
    }
}
