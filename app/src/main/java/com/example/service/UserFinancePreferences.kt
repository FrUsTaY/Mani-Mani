package com.example.service

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

data class PaydayPeriod(
    val startTime: Long,
    val endTime: Long,
    val periodLabel: String,
    val daysUntilPayday: Int,
    val dayOfCycle: Int,
    val totalDaysInCycle: Int
)

enum class AppThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

class UserFinancePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "user_finance_custom_prefs"
        private const val KEY_PAYDAY_DAY = "payday_day_of_month" // 1..31, default 10
        private const val KEY_BANK_OF_THE_MONTH = "bank_of_the_month" // "VTB" or "YANDEX"
        private const val KEY_LAST_DISTRIBUTION_AMOUNT = "last_distribution_amount"
        private const val KEY_PUSH_NOTIFICATIONS_ENABLED = "push_notifications_enabled"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_YANDEX_TOKEN = "yandex_disk_token"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        const val KEY_EVENING_SUMMARY_ENABLED = "evening_summary_enabled"
        const val KEY_EVENING_SUMMARY_TIME = "evening_summary_time"
        private const val KEY_BANK_PUSH_INTERCEPT_ENABLED = "bank_push_intercept_enabled"
        private const val KEY_ZENMONEY_PUSH_INTERCEPT_ENABLED = "zenmoney_push_intercept_enabled"

        const val BANK_VTB = "VTB"
        const val BANK_YANDEX = "YANDEX"
    }

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }
    
    fun getYandexToken(): String {
        return prefs.getString(KEY_YANDEX_TOKEN, "") ?: ""
    }

    fun setYandexToken(token: String) {
        prefs.edit().putString(KEY_YANDEX_TOKEN, token.trim()).apply()
    }
    
    fun getThemeMode(): AppThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try {
            AppThemeMode.valueOf(raw)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun isPushNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_PUSH_NOTIFICATIONS_ENABLED, true)
    }

    fun setPushNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PUSH_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun isBankPushInterceptEnabled(): Boolean {
        return prefs.getBoolean(KEY_BANK_PUSH_INTERCEPT_ENABLED, true)
    }

    fun setBankPushInterceptEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BANK_PUSH_INTERCEPT_ENABLED, enabled).apply()
    }

    fun isZenmoneyPushInterceptEnabled(): Boolean {
        return prefs.getBoolean(KEY_ZENMONEY_PUSH_INTERCEPT_ENABLED, false)
    }

    fun setZenmoneyPushInterceptEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ZENMONEY_PUSH_INTERCEPT_ENABLED, enabled).apply()
    }

    fun isEveningSummaryEnabled(): Boolean {
        return prefs.getBoolean(KEY_EVENING_SUMMARY_ENABLED, false)
    }

    fun setEveningSummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EVENING_SUMMARY_ENABLED, enabled).apply()
    }

    fun getEveningSummaryTime(): String {
        return prefs.getString(KEY_EVENING_SUMMARY_TIME, "21:00") ?: "21:00"
    }

    fun setEveningSummaryTime(time: String) {
        prefs.edit().putString(KEY_EVENING_SUMMARY_TIME, time).apply()
    }

    fun getPaydayDay(): Int {
        return prefs.getInt(KEY_PAYDAY_DAY, 10).coerceIn(1, 31)
    }

    fun setPaydayDay(day: Int) {
        prefs.edit().putInt(KEY_PAYDAY_DAY, day.coerceIn(1, 31)).apply()
    }

    fun getBankOfTheMonth(): String {
        return prefs.getString(KEY_BANK_OF_THE_MONTH, BANK_VTB) ?: BANK_VTB
    }

    fun setBankOfTheMonth(bank: String) {
        prefs.edit().putString(KEY_BANK_OF_THE_MONTH, bank).apply()
    }

    /**
     * Calculates the active financial month based on payday.
     * E.g. If payday is 10:
     * - On Sept 13 -> period is Sept 10 00:00:00 to Oct 09 23:59:59.
     * - On Sept 05 -> period is Aug 10 00:00:00 to Sept 09 23:59:59.
     */
    fun calculatePaydayPeriod(targetPayday: Int = getPaydayDay(), nowTimestamp: Long = System.currentTimeMillis()): PaydayPeriod {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowTimestamp }
        val currentDayOfMonth = nowCal.get(Calendar.DAY_OF_MONTH)

        val cycleStartCal = Calendar.getInstance().apply {
            timeInMillis = nowTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nextPaydayCal = Calendar.getInstance().apply {
            timeInMillis = nowTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (currentDayOfMonth >= targetPayday) {
            // We are on or after payday in current calendar month
            val maxDaysCurrentMonth = cycleStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cycleStartCal.set(Calendar.DAY_OF_MONTH, min(targetPayday, maxDaysCurrentMonth))

            // Next payday is in the next calendar month
            nextPaydayCal.add(Calendar.MONTH, 1)
            val maxDaysNextMonth = nextPaydayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            nextPaydayCal.set(Calendar.DAY_OF_MONTH, min(targetPayday, maxDaysNextMonth))
        } else {
            // We are before payday in current calendar month -> cycle started in previous month
            cycleStartCal.add(Calendar.MONTH, -1)
            val maxDaysPrevMonth = cycleStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cycleStartCal.set(Calendar.DAY_OF_MONTH, min(targetPayday, maxDaysPrevMonth))

            // Next payday is in the current calendar month
            val maxDaysCurrentMonth = nextPaydayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            nextPaydayCal.set(Calendar.DAY_OF_MONTH, min(targetPayday, maxDaysCurrentMonth))
        }

        val cycleStartTime = cycleStartCal.timeInMillis
        val cycleEndTime = nextPaydayCal.timeInMillis - 1L // 23:59:59.999 of day before next payday

        // Formatting label: "10 сен – 9 окт"
        val startFormatter = SimpleDateFormat("d MMM", Locale("ru"))
        val endCal = Calendar.getInstance().apply { timeInMillis = cycleEndTime }
        val endFormatter = SimpleDateFormat("d MMM", Locale("ru"))
        val label = "${startFormatter.format(cycleStartCal.time)} – ${endFormatter.format(endCal.time)}"

        val oneDayMs = 86_400_000L
        val daysUntil = ((nextPaydayCal.timeInMillis - nowTimestamp) / oneDayMs).toInt().coerceAtLeast(0)
        val totalDays = ((nextPaydayCal.timeInMillis - cycleStartTime) / oneDayMs).toInt().coerceAtLeast(1)
        val dayOfCycle = (((nowTimestamp - cycleStartTime) / oneDayMs).toInt() + 1).coerceIn(1, totalDays)

        return PaydayPeriod(
            startTime = cycleStartTime,
            endTime = cycleEndTime,
            periodLabel = label,
            daysUntilPayday = daysUntil,
            dayOfCycle = dayOfCycle,
            totalDaysInCycle = totalDays
        )
    }
}
