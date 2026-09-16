with open('app/src/main/java/com/example/service/EveningSummaryWorker.kt', 'r') as f:
    content = f.read()

import re

old_db = "val db = AppDatabase.getDatabase(applicationContext)"
new_db = "val db = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))"
content = content.replace(old_db, new_db)

old_logic = """val todayExpenses = db.transactionDao().getAllTransactionsSync().filter { 
                it.type == "EXPENSE" && it.timestamp >= startOfDay && !it.excludeFromStats
            }.sumOf { it.amount }"""

new_logic = """val transactions = db.transactionDao().getAllTransactions().firstOrNull() ?: emptyList()
            val todayExpenses = transactions.filter { 
                it.type == "EXPENSE" && it.timestamp >= startOfDay && !it.excludeFromStats
            }.sumOf { it.amount }"""
content = content.replace(old_logic, new_logic)

with open('app/src/main/java/com/example/service/EveningSummaryWorker.kt', 'w') as f:
    f.write(content)
