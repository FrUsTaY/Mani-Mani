with open('app/src/main/java/com/example/service/UserFinancePreferences.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '        private const val KEY_THEME_MODE = "app_theme_mode"',
    '        private const val KEY_THEME_MODE = "app_theme_mode"\n        const val KEY_EVENING_SUMMARY_ENABLED = "evening_summary_enabled"\n        const val KEY_EVENING_SUMMARY_TIME = "evening_summary_time"'
)

with open('app/src/main/java/com/example/service/UserFinancePreferences.kt', 'w') as f:
    f.write(content)
