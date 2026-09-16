import re

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

# 1. Check if itemToExecute exists, if not add it
if 'var itemToExecute' not in content:
    content = content.replace(
        'var itemToEdit by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }',
        'var itemToEdit by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }\n    var itemToExecute by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }'
    )

# 2. Add onExecuteItem to PlannedPaymentsSheet signature
if 'onExecuteItem' not in content[content.find('fun PlannedPaymentsSheet'):content.find('fun PlannedPaymentsSheet')+500]:
    content = content.replace(
        '    onDeleteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit\n) {',
        '    onDeleteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit,\n    onExecuteItem: (com.example.data.entity.PlannedTransactionEntity) -> Unit\n) {'
    )

# 3. Fix double OptIn
while '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)' in content:
    content = content.replace('@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)')

while '@OptIn(ExperimentalMaterial3Api::class)\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)' in content:
    content = content.replace('@OptIn(ExperimentalMaterial3Api::class)\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)', '@OptIn(ExperimentalMaterial3Api::class)')

content = content.replace('@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable')

# 4. Add missing import
if 'import androidx.compose.material3.ExposedDropdownMenu\n' not in content:
    content = content.replace('import androidx.compose.material3.DropdownMenuItem', 'import androidx.compose.material3.DropdownMenuItem\nimport androidx.compose.material3.ExposedDropdownMenu')

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
