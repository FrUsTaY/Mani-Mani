with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    content = f.read()

state_old = "    var showGeminiAssistantScreen by remember { mutableStateOf(false) }"
state_new = "    var showGeminiAssistantScreen by remember { mutableStateOf(false) }\n    var showNotificationSettingsScreen by remember { mutableStateOf(false) }"
content = content.replace(state_old, state_new)

nav_old = "            if (!showBankSyncScreen && !showGeminiAssistantScreen) {"
nav_new = "            if (!showBankSyncScreen && !showGeminiAssistantScreen && !showNotificationSettingsScreen) {"
content = content.replace(nav_old, nav_new)

fab_old = "                visible = !showBankSyncScreen && !showGeminiAssistantScreen && (currentTab == ManiManiNavTab.HOME || currentTab == ManiManiNavTab.HISTORY),"
fab_new = "                visible = !showBankSyncScreen && !showGeminiAssistantScreen && !showNotificationSettingsScreen && (currentTab == ManiManiNavTab.HOME || currentTab == ManiManiNavTab.HISTORY),"
content = content.replace(fab_old, fab_new)

screen_code = """            } else if (showNotificationSettingsScreen) {
                com.example.ui.screens.NotificationSettingsScreen(
                    state = state,
                    onBack = { showNotificationSettingsScreen = false },
                    onTogglePushNotifications = { viewModel.setPushNotificationsEnabled(it) },
                    onSendTestPush = { viewModel.sendTestPushNotification() },
                    onOpenBankSync = {
                        showNotificationSettingsScreen = false
                        showBankSyncScreen = true
                    },
                    isEveningSummaryEnabled = viewModel.isEveningSummaryEnabled(),
                    onToggleEveningSummary = { viewModel.setEveningSummaryEnabled(it) },
                    eveningSummaryTime = viewModel.getEveningSummaryTime(),
                    onSetEveningSummaryTime = { viewModel.setEveningSummaryTime(it) }
                )"""
content = content.replace("            } else if (showGeminiAssistantScreen) {", screen_code + "\n            } else if (showGeminiAssistantScreen) {")

home_old = """                        onOpenBankSync = { showBankSyncScreen = true },
                        onConfirmNotification = { notif, accId, catId ->"""
home_new = """                        onOpenBankSync = { showBankSyncScreen = true },
                        onOpenNotificationSettings = { showNotificationSettingsScreen = true },
                        onConfirmNotification = { notif, accId, catId ->"""
content = content.replace(home_old, home_new)

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
    f.write(content)
