with open('app/src/main/java/com/example/service/UserFinancePreferences.kt', 'r') as f:
    content = f.read()

companion_old = """        private const val KEY_THEME_MODE = "app_theme_mode"
        const val BANK_VTB = "VTB"
        const val BANK_YANDEX = "YANDEX"
    }"""
companion_new = """        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_EVENING_SUMMARY_ENABLED = "evening_summary_enabled"
        private const val KEY_EVENING_SUMMARY_TIME = "evening_summary_time"
        const val BANK_VTB = "VTB"
        const val BANK_YANDEX = "YANDEX"
    }"""

content = content.replace(companion_old, companion_new)

methods = """    fun setPushNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PUSH_NOTIFICATIONS_ENABLED, enabled).apply()
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
    }"""

content = content.replace("""    fun setPushNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PUSH_NOTIFICATIONS_ENABLED, enabled).apply()
    }""", methods)

with open('app/src/main/java/com/example/service/UserFinancePreferences.kt', 'w') as f:
    f.write(content)
