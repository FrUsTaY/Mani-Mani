with open('app/src/main/java/com/example/ui/screens/home/HomeScreen.kt', 'r') as f:
    content = f.read()

sig_old = """    onOpenIncomeDistribution: () -> Unit = {},
    onBankOfTheMonthSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier"""
sig_new = """    onOpenIncomeDistribution: () -> Unit = {},
    onBankOfTheMonthSelect: (String) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier"""

content = content.replace(sig_old, sig_new)

icons_old = """                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onOpenGeminiAssistant(null) },
                        modifier = Modifier.testTag("home_header_gemini_button")
                    ) {"""
icons_new = """                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenNotificationSettings,
                        modifier = Modifier.testTag("home_header_notification_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Уведомления и Ассистент",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(
                        onClick = { onOpenGeminiAssistant(null) },
                        modifier = Modifier.testTag("home_header_gemini_button")
                    ) {"""
content = content.replace(icons_old, icons_new)

with open('app/src/main/java/com/example/ui/screens/home/HomeScreen.kt', 'w') as f:
    f.write(content)
