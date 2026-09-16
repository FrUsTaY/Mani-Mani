with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

# Remove @file:OptIn if it exists
content = content.replace('package com.example.ui.screens.planning\n\n@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)', 'package com.example.ui.screens.planning')
content = content.replace('@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\npackage com.example.ui.screens.planning', 'package com.example.ui.screens.planning')

# Find all @OptIn and remove them
content = content.replace('@OptIn(ExperimentalMaterial3Api::class)', '')
content = content.replace('@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)', '')

# Now re-add them explicitly where needed:
content = content.replace('@Composable\nfun ZenPlansMainView', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun ZenPlansMainView')
content = content.replace('@Composable\nfun AddPlannedPaymentDialog', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun AddPlannedPaymentDialog')
content = content.replace('@Composable\nfun ExecutePlanDialog', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun ExecutePlanDialog')
content = content.replace('@Composable\nfun PlannedPaymentsSheet', '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun PlannedPaymentsSheet')

# fix empty lines
content = content.replace('\n\n\n', '\n\n')

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
