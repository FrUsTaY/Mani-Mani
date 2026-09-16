import re

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'r') as f:
    content = f.read()

old_buttons = r"""                OutlinedButton\(
                    onClick = \{ showClearConfirmDialog = true \},
                    modifier = Modifier
                        \.fillMaxWidth\(\)
                        \.height\(56\.dp\)
                        \.padding\(horizontal = 4\.dp\)
                        \.testTag\("clear_all_data_button"\),
                    shape = RoundedCornerShape\(12\.dp\)
                \) \{
                    Icon\(Icons\.Default\.DeleteSweep, contentDescription = null\)
                    Spacer\(modifier = Modifier\.width\(8\.dp\)\)
                    Text\("Очистить все данные", fontWeight = FontWeight\.SemiBold\)
                \}"""

new_buttons = """                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp)
                        .testTag("clear_all_data_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Очистить все данные", fontWeight = FontWeight.SemiBold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = { 
                        onRestoreDemoData() 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Восстановить демо-данные", fontWeight = FontWeight.SemiBold)
                }"""
content = re.sub(old_buttons, new_buttons, content)

with open('app/src/main/java/com/example/ui/screens/accounts/AccountsSettingsScreen.kt', 'w') as f:
    f.write(content)
