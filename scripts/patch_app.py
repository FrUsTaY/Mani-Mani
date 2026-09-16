import re

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'r') as f:
    content = f.read()

content = re.sub(r'\s*onRestoreDemoData\s*=\s*\{\s*viewModel\.resetToDemoData\(\)\s*\},', '', content)

with open('app/src/main/java/com/example/ui/ManiManiApp.kt', 'w') as f:
    f.write(content)
