import re

with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'r') as f:
    content = f.read()

# Add goalId and debtId to TransactionEntity
txn_old = r"""    val note: String = "",
    val tag: String = "",
    val excludeFromStats: Boolean = false
\)"""
txn_new = """    val note: String = "",
    val tag: String = "",
    val excludeFromStats: Boolean = false,
    val goalId: Long? = null,
    val debtId: Long? = null
)"""
content = re.sub(txn_old, txn_new, content)

with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'w') as f:
    f.write(content)
