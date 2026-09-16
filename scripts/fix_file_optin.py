with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

if '@file:OptIn' not in content:
    content = content.replace('package com.example.ui.screens.planning', 'package com.example.ui.screens.planning\n\n@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)')

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
