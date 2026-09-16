with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

import re
pattern = r'Text\(\s*text = "Автоматизация и пуш-уведомления",.*?Text\("Синхронизация с банками \(Пуши / Чек\)", fontWeight = FontWeight\.SemiBold\)\s*\}\s*Spacer\(modifier = Modifier\.height\(8\.dp\)\)'

new_content = re.sub(pattern, 'Text(\n                    text = "Управление данными",\n                    style = MaterialTheme.typography.titleMedium,\n                    fontWeight = FontWeight.Bold\n                )\n                Spacer(modifier = Modifier.height(8.dp))', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(new_content)
