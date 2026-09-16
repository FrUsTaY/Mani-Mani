import os
import re

files_to_update = [
    'app/src/main/java/com/example/ui/screens/home/HomeScreen.kt',
    'app/src/main/java/com/example/ui/screens/transactions/TransactionsScreen.kt',
    'app/src/main/java/com/example/ui/screens/analytics/AnalyticsDetailSheets.kt'
]

for filepath in files_to_update:
    with open(filepath, 'r') as f:
        content = f.read()
    
    # 1. Create maps near where accountsMap is created
    # Find "val accountsMap ="
    map_code = """    val accountsMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val goalsMap = remember(state.goals) { state.goals.associateBy { it.id } }
    val debtsMap = remember(state.debts) { state.debts.associateBy { it.id } }"""
    content = re.sub(r'    val accountsMap = remember\(state\.accounts\) \{ state\.accounts\.associateBy \{ it\.id \} \}', map_code, content)
    
    # 2. Add them to TransactionItemCard
    card_code = """                            TransactionItemCard(
                                transaction = tx,
                                accountsMap = accountsMap,
                                categoriesMap = categoriesMap,
                                goalsMap = goalsMap,
                                debtsMap = debtsMap,"""
    content = re.sub(r'                            TransactionItemCard\(\n                                transaction = tx,\n                                accountsMap = accountsMap,\n                                categoriesMap = categoriesMap,', card_code, content)
    
    with open(filepath, 'w') as f:
        f.write(content)

print("Done")
