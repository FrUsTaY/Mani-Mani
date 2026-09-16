import re

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

# Replace application with getApplication() in EveningSummaryScheduler calls
content = content.replace("EveningSummaryScheduler.schedule(application,", "EveningSummaryScheduler.schedule(getApplication(),")
content = content.replace("EveningSummaryScheduler.cancel(application)", "EveningSummaryScheduler.cancel(getApplication())")


# Move the summary states above init block
state_block = """    private val _isEveningSummaryEnabled = MutableStateFlow(userFinancePrefs.isEveningSummaryEnabled())
    val isEveningSummaryEnabledFlow: StateFlow<Boolean> = _isEveningSummaryEnabled.asStateFlow()

    private val _eveningSummaryTime = MutableStateFlow(userFinancePrefs.getEveningSummaryTime())
    val eveningSummaryTimeFlow: StateFlow<String> = _eveningSummaryTime.asStateFlow()
"""

# Remove them from old place
content = content.replace(state_block, "")

# Insert before init {
init_str = "    init {"
content = content.replace(init_str, state_block + "\n" + init_str)


with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
