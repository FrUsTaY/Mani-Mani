with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

content = content.replace('androidx.compose.material3.ExposedDropdownMenu(', 'ExposedDropdownMenu(')
content = content.replace('androidx.compose.material3.DropdownMenuItem(', 'DropdownMenuItem(')

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
