package com.example

import com.example.ui.screens.transactions.HistoryPeriodHelper
import com.example.ui.screens.transactions.HistoryPeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class HistoryPeriodHelperTest {

    @Test
    fun `test week range starts on Monday and ends on Sunday`() {
        val cal = Calendar.getInstance()
        // Set to Wednesday, Sept 23, 2026, 14:30:00
        cal.set(2026, Calendar.SEPTEMBER, 23, 14, 30, 0)
        val wednesdayMillis = cal.timeInMillis

        val (start, end) = HistoryPeriodHelper.getWeekRange(wednesdayMillis)

        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }

        // Start should be Monday, Sept 21, 2026, 00:00:00.000
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(21, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, startCal.get(Calendar.MONTH))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.SECOND))
        assertEquals(0, startCal.get(Calendar.MILLISECOND))

        // End should be Sunday, Sept 27, 2026, 23:59:59.999
        assertEquals(Calendar.SUNDAY, endCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(27, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, endCal.get(Calendar.MONTH))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(59, endCal.get(Calendar.SECOND))
        assertEquals(999, endCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `test week range when today is Sunday`() {
        val cal = Calendar.getInstance()
        // Sunday, Sept 27, 2026, 22:00:00
        cal.set(2026, Calendar.SEPTEMBER, 27, 22, 0, 0)
        val sundayMillis = cal.timeInMillis

        val (start, end) = HistoryPeriodHelper.getWeekRange(sundayMillis)

        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(21, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SUNDAY, endCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(27, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `test week range when today is Monday`() {
        val cal = Calendar.getInstance()
        // Monday, Sept 21, 2026, 01:00:00
        cal.set(2026, Calendar.SEPTEMBER, 21, 1, 0, 0)
        val mondayMillis = cal.timeInMillis

        val (start, end) = HistoryPeriodHelper.getWeekRange(mondayMillis)

        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(21, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SUNDAY, endCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(27, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `test month range covers entire calendar month`() {
        val cal = Calendar.getInstance()
        // September 15, 2026
        cal.set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
        val (start, end) = HistoryPeriodHelper.getMonthRange(cal.timeInMillis)

        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, startCal.get(Calendar.MONTH))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))

        assertEquals(30, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, endCal.get(Calendar.MONTH))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(59, endCal.get(Calendar.SECOND))
    }

    @Test
    fun `test normalize custom range`() {
        val cal1 = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 10, 15, 20, 0) }
        val cal2 = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 1, 8, 10, 0) }

        // Pass in reversed order (June 10 to June 1)
        val (start, end) = HistoryPeriodHelper.normalizeCustomRange(cal1.timeInMillis, cal2.timeInMillis)

        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))

        assertEquals(10, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `test format operations count with Russian plural forms`() {
        assertEquals("0 операций", HistoryPeriodHelper.formatOperationsCount(0))
        assertEquals("1 операция", HistoryPeriodHelper.formatOperationsCount(1))
        assertEquals("2 операции", HistoryPeriodHelper.formatOperationsCount(2))
        assertEquals("3 операции", HistoryPeriodHelper.formatOperationsCount(3))
        assertEquals("4 операции", HistoryPeriodHelper.formatOperationsCount(4))
        assertEquals("5 операций", HistoryPeriodHelper.formatOperationsCount(5))
        assertEquals("11 операций", HistoryPeriodHelper.formatOperationsCount(11))
        assertEquals("12 операций", HistoryPeriodHelper.formatOperationsCount(12))
        assertEquals("21 операция", HistoryPeriodHelper.formatOperationsCount(21))
        assertEquals("22 операции", HistoryPeriodHelper.formatOperationsCount(22))
        assertEquals("27 операций", HistoryPeriodHelper.formatOperationsCount(27))
        assertEquals("143 операции", HistoryPeriodHelper.formatOperationsCount(143))
        // 2841 should be formatted as 2 841 операция
        val formatted2841 = HistoryPeriodHelper.formatOperationsCount(2841)
        assertTrue(formatted2841.contains("2") && formatted2841.contains("841") && formatted2841.endsWith("операция"))
    }

    @Test
    fun `test format period label`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 23, 12, 0, 0) }
        val now = cal.timeInMillis

        val weekLabel = HistoryPeriodHelper.formatPeriodLabel(HistoryPeriodType.WEEK, now = now)
        assertTrue(weekLabel.contains("21") && weekLabel.contains("27") && weekLabel.contains("—"))

        val monthLabel = HistoryPeriodHelper.formatPeriodLabel(HistoryPeriodType.MONTH, now = now)
        assertTrue(monthLabel.contains("2026") && monthLabel.lowercase(Locale("ru")).contains("сентябр"))

        val allTimeLabel = HistoryPeriodHelper.formatPeriodLabel(HistoryPeriodType.ALL_TIME, now = now)
        assertEquals("Всё время", allTimeLabel)

        val customStartCal = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 1, 0, 0, 0) }
        val customEndCal = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 30, 23, 59, 59) }
        val customLabel = HistoryPeriodHelper.formatPeriodLabel(
            HistoryPeriodType.CUSTOM,
            customStart = customStartCal.timeInMillis,
            customEnd = customEndCal.timeInMillis,
            now = now
        )
        assertTrue(customLabel.contains("01") && customLabel.contains("30") && customLabel.contains("—"))
    }
}
