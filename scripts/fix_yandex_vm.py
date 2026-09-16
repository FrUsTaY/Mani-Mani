import re

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'r') as f:
    content = f.read()

# I will just revert any "yandexToken" from FinanceUiState and AppConfig because I don't need it there anyway.
# We fetch it directly using userFinancePrefs.getYandexToken() in the UI.

content = re.sub(r',\s*val yandexToken: String = ""', '', content)
content = re.sub(r',\s*val yandexToken: String', '', content)
content = re.sub(r',\s*yandexToken = userFinancePrefs.getYandexToken\(\)', '', content)

with open('app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt', 'w') as f:
    f.write(content)
