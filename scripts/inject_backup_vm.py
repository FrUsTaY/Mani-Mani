import re

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

# First replace the constructor
content = content.replace(
    'class FinanceViewModel(application: Application) : AndroidViewModel(application) {',
    'class FinanceViewModel(application: Application) : AndroidViewModel(application) {\n    val backupRepository: BackupRepository'
)

content = content.replace(
    '        repository = FinanceRepository(database)',
    '        repository = FinanceRepository(database)\n        backupRepository = BackupRepository(application, database.accountDao(), database.categoryDao(), database.transactionDao(), database.budgetDao(), database.goalDao(), database.debtDao(), database.plannedTransactionDao(), userFinancePrefs)'
)

# Then append the Backup functions at the end before the last closing brace
backup_funcs = """

    // --- Backup & Restore ---
    fun exportBackupLocal(uri: Uri) {
        viewModelScope.launch {
            try {
                backupRepository.exportLocal(uri)
                _statusMessage.value = "Локальный бэкап успешно сохранен"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка при экспорте: ${e.message}"
            }
        }
    }

    fun importBackupLocal(uri: Uri) {
        viewModelScope.launch {
            try {
                backupRepository.importLocal(uri)
                _statusMessage.value = "Данные успешно восстановлены из файла"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка при импорте: ${e.message}"
            }
        }
    }

    fun exportBackupCloud(token: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Сохраняем данные в Яндекс.Диск..."
                backupRepository.exportCloud(token)
                _statusMessage.value = "Бэкап успешно сохранен в Яндекс.Диск"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка облачного экспорта: ${e.message}"
            }
        }
    }

    fun importBackupCloud(token: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Загружаем данные из Яндекс.Диска..."
                backupRepository.importCloud(token)
                _statusMessage.value = "Данные из облака успешно восстановлены"
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка облачного импорта: ${e.message}"
            }
        }
    }
"""

# Inject before the last closing brace
content = content.rsplit('}', 1)
content = content[0] + backup_funcs + "\n}"

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
