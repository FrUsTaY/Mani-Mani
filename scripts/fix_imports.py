with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

imports_to_add = [
    "import androidx.compose.material3.ExposedDropdownMenuBox",
    "import androidx.compose.material3.ExposedDropdownMenuDefaults",
    "import androidx.compose.material3.DropdownMenuItem"
]

for imp in imports_to_add:
    if imp not in content:
        content = content.replace("import androidx.compose.material3.Text", f"{imp}\nimport androidx.compose.material3.Text")

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
