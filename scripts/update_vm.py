import re

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

old_add = r"""    fun addAccount\(
        name: String,
        type: String,
        initialBalance: Double,
        currency: String,
        colorHex: String,
        iconName: String
    \) \{
        viewModelScope\.launch \{
            repository\.insertAccount\(
                AccountEntity\(
                    name = name,
                    type = type,
                    balance = initialBalance,
                    currency = currency,
                    colorHex = colorHex,
                    iconName = iconName
                \)
            \)
            _statusMessage\.value = "Счёт «\$name» создан"
        \}
    \}"""

new_add = """    fun addAccount(
        name: String,
        type: String,
        initialBalance: Double,
        currency: String,
        colorHex: String,
        iconName: String,
        includeInTotal: Boolean,
        includeInAnalytics: Boolean
    ) {
        viewModelScope.launch {
            repository.insertAccount(
                AccountEntity(
                    name = name,
                    type = type,
                    balance = initialBalance,
                    currency = currency,
                    colorHex = colorHex,
                    iconName = iconName,
                    includeInTotal = includeInTotal,
                    includeInAnalytics = includeInAnalytics
                )
            )
            _statusMessage.value = "Счёт «$name» создан"
        }
    }"""

content = re.sub(old_add, new_add, content)

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
print("Done")
