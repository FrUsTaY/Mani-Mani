with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

content = content.replace('@Composable\n@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable')

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
