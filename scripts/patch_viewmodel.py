import re

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

# Add BackupRepository to constructor and property
if 'val backupRepository: BackupRepository' not in content:
    # First, import BackupRepository
    content = content.replace('import com.example.data.repository.FinanceRepository', 'import com.example.data.repository.FinanceRepository\nimport com.example.data.repository.BackupRepository\nimport android.net.Uri')
    
    # Then add it to constructor
    content = re.sub(
        r'class FinanceViewModel\(\n    val repository: FinanceRepository,\n    private val preferences: UserFinancePreferences\n\)',
        'class FinanceViewModel(\n    val repository: FinanceRepository,\n    val backupRepository: BackupRepository,\n    private val preferences: UserFinancePreferences\n)',
        content
    )
    
    content = re.sub(
        r'class FinanceViewModelFactory\(\n    private val repository: FinanceRepository,\n    private val preferences: UserFinancePreferences\n\)',
        'class FinanceViewModelFactory(\n    private val repository: FinanceRepository,\n    private val backupRepository: BackupRepository,\n    private val preferences: UserFinancePreferences\n)',
        content
    )
    
    content = re.sub(
        r'return FinanceViewModel\(repository, preferences\) as T',
        'return FinanceViewModel(repository, backupRepository, preferences) as T',
        content
    )

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
