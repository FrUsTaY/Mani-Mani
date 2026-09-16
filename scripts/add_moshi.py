import re

with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'r') as f:
    content = f.read()

content = "import com.squareup.moshi.JsonClass\n" + content
content = re.sub(r'(@Entity[^\n]*\n)(data class)', r'\1@JsonClass(generateAdapter = true)\n\2', content)

with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'w') as f:
    f.write(content)

