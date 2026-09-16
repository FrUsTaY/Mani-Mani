import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

if 'Clear All Data Confirmation Dialog' in content:
    print("Clear data dialog exists.")
else:
    print("Missing clear data dialog.")
