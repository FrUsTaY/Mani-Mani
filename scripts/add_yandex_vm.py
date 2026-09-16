import re

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

if 'val yandexToken: String = ""' not in content:
    content = content.replace('val themeMode: AppThemeMode = AppThemeMode.SYSTEM', 'val themeMode: AppThemeMode = AppThemeMode.SYSTEM,\n    val yandexToken: String = ""')
    
    content = content.replace('val themeMode: AppThemeMode', 'val themeMode: AppThemeMode,\n    val yandexToken: String')
    content = content.replace('themeMode = extras[3] as AppThemeMode', 'themeMode = extras[3] as AppThemeMode,\n            yandexToken = userFinancePrefs.getYandexToken()')

    methods = """
    fun setYandexToken(token: String) {
        userFinancePrefs.setYandexToken(token)
        // trigger flow update, but actually it's not in the flow directly. Let's make it a StateFlow.
    }
    """
    # Just update the preferences, it's easier to just read from state or prefs directly in UI.

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
