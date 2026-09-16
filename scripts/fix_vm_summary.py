with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

import re

old_logic = """    fun setEveningSummaryEnabled(enabled: Boolean) {
        userFinancePrefs.setEveningSummaryEnabled(enabled)
        _isEveningSummaryEnabled.value = enabled
    }

    fun getEveningSummaryTime(): String {
        return _eveningSummaryTime.value
    }

    fun setEveningSummaryTime(time: String) {
        userFinancePrefs.setEveningSummaryTime(time)
        _eveningSummaryTime.value = time
    }"""

new_logic = """    fun setEveningSummaryEnabled(enabled: Boolean) {
        userFinancePrefs.setEveningSummaryEnabled(enabled)
        _isEveningSummaryEnabled.value = enabled
        if (enabled) {
            com.example.service.EveningSummaryScheduler.schedule(application, _eveningSummaryTime.value)
        } else {
            com.example.service.EveningSummaryScheduler.cancel(application)
        }
    }

    fun getEveningSummaryTime(): String {
        return _eveningSummaryTime.value
    }

    fun setEveningSummaryTime(time: String) {
        userFinancePrefs.setEveningSummaryTime(time)
        _eveningSummaryTime.value = time
        if (_isEveningSummaryEnabled.value) {
            com.example.service.EveningSummaryScheduler.schedule(application, time)
        }
    }"""

content = content.replace(old_logic, new_logic)

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
