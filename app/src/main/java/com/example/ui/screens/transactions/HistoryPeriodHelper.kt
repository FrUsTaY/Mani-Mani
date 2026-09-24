package com.example.ui.screens.transactions

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryPeriodType(val title: String) {
    WEEK("Неделя"),
    MONTH("Месяц"),
    ALL_TIME("Всё время"),
    CUSTOM("Период");

    companion object {
        fun fromString(value: String): HistoryPeriodType {
            return try {
                valueOf(value)
            } catch (e: Exception) {
                WEEK
            }
        }
    }
}

object HistoryPeriodHelper {

    fun getWeekRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon... 7=Sat
        val daysFromMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfWeek = cal.timeInMillis

        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfWeek = cal.timeInMillis
        return Pair(startOfWeek, endOfWeek)
    }

    fun getMonthRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfMonth = cal.timeInMillis
        return Pair(startOfMonth, endOfMonth)
    }

    fun normalizeCustomRange(startMillis: Long, endMillis: Long): Pair<Long, Long> {
        val s = minOf(startMillis, endMillis)
        val e = maxOf(startMillis, endMillis)
        val calStart = Calendar.getInstance().apply {
            timeInMillis = s
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply {
            timeInMillis = e
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Pair(calStart.timeInMillis, calEnd.timeInMillis)
    }

    fun utcMillisToLocalDayRange(utcStartMillis: Long, utcEndMillis: Long): Pair<Long, Long> {
        val s = minOf(utcStartMillis, utcEndMillis)
        val e = maxOf(utcStartMillis, utcEndMillis)

        val utcStartCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = s }
        val localStartCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcStartCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcStartCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcStartCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val utcEndCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = e }
        val localEndCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcEndCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcEndCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcEndCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        return Pair(localStartCal.timeInMillis, localEndCal.timeInMillis)
    }

    fun formatPeriodLabel(
        type: HistoryPeriodType,
        customStart: Long? = null,
        customEnd: Long? = null,
        now: Long = System.currentTimeMillis()
    ): String {
        return when (type) {
            HistoryPeriodType.WEEK -> {
                val (start, end) = getWeekRange(now)
                val fmt = SimpleDateFormat("d MMM", Locale("ru"))
                "${fmt.format(Date(start))} — ${fmt.format(Date(end))}"
            }
            HistoryPeriodType.MONTH -> {
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                SimpleDateFormat("LLLL yyyy", Locale("ru")).format(cal.time)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
            }
            HistoryPeriodType.ALL_TIME -> "Всё время"
            HistoryPeriodType.CUSTOM -> {
                if (customStart != null && customEnd != null) {
                    val startCal = Calendar.getInstance().apply { timeInMillis = customStart }
                    val endCal = Calendar.getInstance().apply { timeInMillis = customEnd }
                    val sameYear = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)
                    val currentYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
                    val fmt = if (sameYear && startCal.get(Calendar.YEAR) == currentYear) {
                        SimpleDateFormat("dd MMM", Locale("ru"))
                    } else {
                        SimpleDateFormat("dd MMM yyyy", Locale("ru"))
                    }
                    "${fmt.format(Date(customStart))} — ${fmt.format(Date(customEnd))}"
                } else {
                    "Период"
                }
            }
        }
    }

    fun formatOperationsCount(count: Int): String {
        val rem100 = count % 100
        val rem10 = count % 10
        val word = when {
            rem100 in 11..19 -> "операций"
            rem10 == 1 -> "операция"
            rem10 in 2..4 -> "операции"
            else -> "операций"
        }
        val symbols = DecimalFormatSymbols(Locale("ru")).apply { groupingSeparator = ' ' }
        val formattedCount = DecimalFormat("#,##0", symbols).format(count)
        return "$formattedCount $word"
    }
}
