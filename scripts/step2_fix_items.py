import re

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

loop_old = """                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${item.dayOfMonth}-е число месяца • ${if (item.isIncome) "Поступление" else "Платеж"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = (if (item.isIncome) "+ " else "- ") + CurrencyHelper.formatAmount(item.amount, currency),
                                fontWeight = FontWeight.Bold,
                                color = if (item.isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { onDeleteItem(item.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))
                    }"""

loop_new = """                    items.forEach { item ->
                        val cal = Calendar.getInstance().apply { timeInMillis = item.plannedDate }
                        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                        val isIncome = item.type == "INCOME"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditItem(item) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.note,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${dayOfMonth}-е число месяца • ${if (isIncome) "Поступление" else "Платеж"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = (if (isIncome) "+ " else "- ") + CurrencyHelper.formatAmount(item.amount, currency),
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { onDeleteItem(item) }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 56.dp))
                    }"""

# The previous regex might not have matched exactly, let's just find `items.forEach { item ->` and replace the block
# Actually, I can use re to replace everything between `items.forEach { item ->` and the next `}` block.
# Or simpler:
if "item.title" in content and "items.forEach { item ->" in content:
    start_idx = content.find("items.forEach { item ->")
    end_idx = content.find("HorizontalDivider", start_idx)
    end_idx = content.find("}", end_idx) + 1
    content = content[:start_idx] + loop_new.strip() + content[end_idx:]

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
print("Items updated")
