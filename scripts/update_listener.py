with open('app/src/main/java/com/example/service/BankNotificationListener.kt', 'r') as f:
    content = f.read()

import re

old_block = r"val entity = PendingNotificationEntity\([\s\S]*?db\.pendingNotificationDao\(\)\.insertNotification\(entity\)"

new_block = """val rawTextVal = "${title?.let { "$it: " } ?: ""}$text"
                    val timestampVal = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
                    
                    // Duplicate check: same text within the last 10 minutes
                    val duplicate = db.pendingNotificationDao().findRecentDuplicateByText(
                        rawText = rawTextVal,
                        sinceTime = timestampVal - 10 * 60 * 1000L
                    )
                    
                    if (duplicate != null) {
                        Log.d("BankNotificationListener", "Skipping duplicate notification: $rawTextVal")
                        return@launch
                    }

                    val entity = PendingNotificationEntity(
                        packageName = packageName,
                        bankName = parsed.bankName,
                        rawText = rawTextVal,
                        type = parsed.type,
                        amount = parsed.amount,
                        currency = parsed.currency,
                        merchantOrSender = parsed.merchant,
                        cardLast4 = parsed.cardLast4,
                        suggestedCategoryId = suggestedCatId,
                        suggestedAccountId = matchedAccount?.id,
                        timestamp = timestampVal
                    )
                    db.pendingNotificationDao().insertNotification(entity)"""

content = re.sub(old_block, new_block, content)

with open('app/src/main/java/com/example/service/BankNotificationListener.kt', 'w') as f:
    f.write(content)
