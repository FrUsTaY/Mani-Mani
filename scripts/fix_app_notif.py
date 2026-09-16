with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    content = f.read()

screen_code = """            } else if (showNotificationSettingsScreen) {
                val isEveningSummaryEnabled by viewModel.isEveningSummaryEnabledFlow.collectAsState()
                val eveningSummaryTime by viewModel.eveningSummaryTimeFlow.collectAsState()
                com.example.ui.screens.NotificationSettingsScreen(
                    state = state,
                    onBack = { showNotificationSettingsScreen = false },
                    onTogglePushNotifications = { viewModel.setPushNotificationsEnabled(it) },
                    onSendTestPush = { viewModel.sendTestPushNotification() },
                    onOpenBankSync = {
                        showNotificationSettingsScreen = false
                        showBankSyncScreen = true
                    },
                    isEveningSummaryEnabled = isEveningSummaryEnabled,
                    onToggleEveningSummary = { viewModel.setEveningSummaryEnabled(it) },
                    eveningSummaryTime = eveningSummaryTime,
                    onSetEveningSummaryTime = { viewModel.setEveningSummaryTime(it) }
                )"""

content = content.replace("            } else if (showBankSyncScreen) {", screen_code + "\n            } else if (showBankSyncScreen) {")

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
    f.write(content)
