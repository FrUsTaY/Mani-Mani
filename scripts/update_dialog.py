import re

with open('app/src/main/java/com/example/ui/screens/accounts/AddEditAccountDialog.kt', 'r') as f:
    content = f.read()

sig_old = r"""    onSave: \(
        name: String,
        type: String,
        balance: Double,
        currency: String,
        colorHex: String,
        iconName: String
    \) -> Unit"""

sig_new = """    onSave: (
        name: String,
        type: String,
        balance: Double,
        currency: String,
        colorHex: String,
        iconName: String,
        includeInTotal: Boolean,
        includeInAnalytics: Boolean
    ) -> Unit"""

content = re.sub(sig_old, sig_new, content)

states_old = r"""    var selectedIcon by remember \{ mutableStateOf\(initialAccount\?\.iconName \?: "credit_card"\) \}
    var errorMessage by remember \{ mutableStateOf<String\?>\(null\) \}"""

states_new = """    var selectedIcon by remember { mutableStateOf(initialAccount?.iconName ?: "credit_card") }
    var includeInTotal by remember { mutableStateOf(initialAccount?.includeInTotal ?: true) }
    var includeInAnalytics by remember { mutableStateOf(initialAccount?.includeInAnalytics ?: true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }"""

content = re.sub(states_old, states_new, content)

with open('app/src/main/java/com/example/ui/screens/accounts/AddEditAccountDialog.kt', 'w') as f:
    f.write(content)
print("Done")
