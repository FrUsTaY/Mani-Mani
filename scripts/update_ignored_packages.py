with open('app/src/main/java/com/example/service/BankNotificationListener.kt', 'r') as f:
    content = f.read()

import re

ignore_logic = """        if (packageName == applicationContext.packageName) {
            return
        }

        // CRITICAL: Ignore other personal finance apps to avoid double counting (they also intercept bank pushes)
        val ignoredPackages = listOf(
            "ru.zenmoney.android",
            "com.coinkeeper.android",
            "com.monefy.app.lite",
            "com.monefy.app.pro",
            "com.innofinapps.1money",
            "com.orion.cashew"
        )
        if (ignoredPackages.any { packageName.contains(it, ignoreCase = true) }) {
            return
        }"""

content = content.replace("""        // CRITICAL: Prevent infinite loops by ignoring our own notifications
        if (packageName == applicationContext.packageName) {
            return
        }""", ignore_logic)


with open('app/src/main/java/com/example/service/BankNotificationListener.kt', 'w') as f:
    f.write(content)
