with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

content = content.replace('@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@OptIn(ExperimentalMaterial3Api::class)', '@OptIn(ExperimentalMaterial3Api::class)')
content = content.replace('@OptIn(ExperimentalMaterial3Api::class)\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)', '@OptIn(ExperimentalMaterial3Api::class)')
content = content.replace('@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable', '@Composable')

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
