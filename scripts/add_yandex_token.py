import re

with open('app/src/main/java/com/example/service/UserFinancePreferences.kt', 'r') as f:
    content = f.read()

if 'KEY_YANDEX_TOKEN' not in content:
    content = content.replace('private const val KEY_THEME_MODE = "app_theme_mode"', 'private const val KEY_THEME_MODE = "app_theme_mode"\n        private const val KEY_YANDEX_TOKEN = "yandex_disk_token"')
    
    methods = """
    fun getYandexToken(): String {
        return prefs.getString(KEY_YANDEX_TOKEN, "") ?: ""
    }

    fun setYandexToken(token: String) {
        prefs.edit().putString(KEY_YANDEX_TOKEN, token.trim()).apply()
    }
    """
    content = content.replace('fun getThemeMode(): AppThemeMode {', methods + '\n    fun getThemeMode(): AppThemeMode {')

with open('app/src/main/java/com/example/service/UserFinancePreferences.kt', 'w') as f:
    f.write(content)
