with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

import re

old_logic = """    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(database)
    }"""

new_logic = """    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(database)
        
        // Restore evening summary schedule if enabled
        if (_isEveningSummaryEnabled.value) {
            com.example.service.EveningSummaryScheduler.schedule(application, _eveningSummaryTime.value)
        }
    }"""

content = content.replace(old_logic, new_logic)

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
