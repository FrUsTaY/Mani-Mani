with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

methods = """
    fun isEveningSummaryEnabled(): Boolean {
        return preferences.isEveningSummaryEnabled()
    }

    fun setEveningSummaryEnabled(enabled: Boolean) {
        preferences.setEveningSummaryEnabled(enabled)
        _uiState.update { it.copy() } // trigger recomposition
    }

    fun getEveningSummaryTime(): String {
        return preferences.getEveningSummaryTime()
    }

    fun setEveningSummaryTime(time: String) {
        preferences.setEveningSummaryTime(time)
        _uiState.update { it.copy() } // trigger recomposition
    }
"""

content = content.replace("    fun setPushNotificationsEnabled(enabled: Boolean) {", methods + "\n    fun setPushNotificationsEnabled(enabled: Boolean) {")

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
