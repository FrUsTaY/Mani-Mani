with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'r') as f:
    content = f.read()

sig_old = """    onAddPlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onUpdatePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},"""
sig_new = """    onAddPlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onUpdatePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onDeletePlannedTransaction: (com.example.data.entity.PlannedTransactionEntity) -> Unit = {},
    onAddTransaction: (com.example.data.entity.TransactionEntity) -> Unit = {},"""
content = content.replace(sig_old, sig_new)

state_old = """    // Dialog states
    var showPlannedPaymentsSheet by remember { mutableStateOf(false) }
    var showAddPlannedPaymentDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }"""
state_new = """    // Dialog states
    var showPlannedPaymentsSheet by remember { mutableStateOf(false) }
    var showAddPlannedPaymentDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }
    var itemToExecute by remember { mutableStateOf<com.example.data.entity.PlannedTransactionEntity?>(null) }"""
content = content.replace(state_old, state_new)

with open('app/src/main/java/com/example/ui/screens/planning/ZenPlansMainView.kt', 'w') as f:
    f.write(content)
