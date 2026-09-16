package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.entity.PendingNotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class BankNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMutex = Mutex()

    companion object {
        // Cache of recently processed notification keys (sbn.key) with timestamp (60s TTL).
        // Prevents re-processing when the bank app updates an already posted notification.
        private val processedNotificationKeys = ConcurrentHashMap<String, Long>()

        // Debounce cache of recent transaction signatures (pkg_amount_type_merchant) for 2.5 seconds.
        // Prevents near-simultaneous duplicate push broadcasts from the OS/app,
        // while allowing genuine consecutive payments (e.g. paying for another person on transit after 5-10s).
        private val recentTransactionSignatures = ConcurrentHashMap<String, Long>()

        private const val SBN_KEY_CACHE_TTL_MS = 60_000L
        private const val SIGNATURE_DEBOUNCE_MS = 2_500L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        
        if (packageName == applicationContext.packageName) {
            return
        }

        // Check intercept settings
        val userPrefs = UserFinancePreferences(applicationContext)
        val isBankInterceptEnabled = userPrefs.isBankPushInterceptEnabled()
        val isZenmoneyInterceptEnabled = userPrefs.isZenmoneyPushInterceptEnabled()

        // 1. Ignore group summaries (cards that merely summarize multiple notifications in Android)
        val flags = sbn.notification.flags
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d("BankNotificationListener", "Skipping group summary notification from $packageName")
            return
        }

        // 2. Ignore other personal finance apps to avoid double counting (they also intercept bank pushes)
        val ignoredPackages = listOf(
            "com.coinkeeper.android",
            "com.monefy.app.lite",
            "com.monefy.app.pro",
            "com.innofinapps.1money",
            "com.orion.cashew"
        )
        if (ignoredPackages.any { packageName.contains(it, ignoreCase = true) }) {
            return
        }

        val isZenmoney = packageName == "ru.zenmoney.androidsub" || packageName == "ru.zenmoney.android"
        
        if (isZenmoney && !isZenmoneyInterceptEnabled) {
            return
        }

        val isKnownBank = BankNotificationParser.KNOWN_BANK_PACKAGES.containsKey(packageName)
        if (isKnownBank && !isBankInterceptEnabled) {
            return
        }

        val currentTime = System.currentTimeMillis()

        // Prune stale cache entries periodically to avoid memory leaks
        if (processedNotificationKeys.size > 50) {
            processedNotificationKeys.entries.removeIf { currentTime - it.value > SBN_KEY_CACHE_TTL_MS }
        }
        if (recentTransactionSignatures.size > 50) {
            recentTransactionSignatures.entries.removeIf { currentTime - it.value > SIGNATURE_DEBOUNCE_MS }
        }

        // 3. In-memory check: Has this exact notification key already been processed?
        val sbnKey = sbn.key
        if (sbnKey != null) {
            val lastSeen = processedNotificationKeys[sbnKey]
            if (lastSeen != null && (currentTime - lastSeen) < SBN_KEY_CACHE_TTL_MS) {
                Log.d("BankNotificationListener", "Skipping already processed sbn.key: $sbnKey")
                return
            }
        }

        val extras = sbn.notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        if (text.isNullOrBlank()) return

        // Check if package belongs to known banks or notification text mentions financial transaction keywords
        val isKnownBankPackage = BankNotificationParser.KNOWN_BANK_PACKAGES.containsKey(packageName)
        val fullText = "${title ?: ""} $text".lowercase()
        val hasFinancialKeywords = fullText.contains("покупка") || fullText.contains("списание") ||
                fullText.contains("зачисление") || fullText.contains("перевод") ||
                fullText.contains("оплата") || fullText.contains("баланс")

        if (!isKnownBankPackage && !hasFinancialKeywords) {
            return
        }

        val parsed = BankNotificationParser.parse(text, title, packageName)
        if (parsed != null && parsed.amount > 0) {
            // 4. Short-window signature debounce check (2.5 seconds)
            val signature = "${packageName}_${parsed.amount}_${parsed.type}_${parsed.merchant}"
            val lastSigTime = recentTransactionSignatures[signature]
            if (lastSigTime != null && (currentTime - lastSigTime) < SIGNATURE_DEBOUNCE_MS) {
                Log.d("BankNotificationListener", "Skipping rapid duplicate signature within 2.5s: $signature")
                return
            }

            // Mark key and signature as processed immediately in memory
            if (sbnKey != null) {
                processedNotificationKeys[sbnKey] = currentTime
            }
            recentTransactionSignatures[signature] = currentTime

            Log.d("BankNotificationListener", "Intercepted bank notification: $parsed from $packageName")

            serviceScope.launch {
                processingMutex.withLock {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext, serviceScope)
                        val timestampVal = sbn.postTime.takeIf { it > 0 } ?: currentTime
                        val rawTextVal = "${title?.let { "$it: " } ?: ""}$text"

                        // Safety check: duplicate in DB within the last 3 seconds
                        val duplicate = db.pendingNotificationDao().findRecentDuplicateByDetails(
                            packageName = packageName,
                            amount = parsed.amount,
                            type = parsed.type,
                            sinceTime = timestampVal - SIGNATURE_DEBOUNCE_MS
                        ) ?: db.pendingNotificationDao().findRecentDuplicateByText(
                            rawText = rawTextVal,
                            sinceTime = timestampVal - SIGNATURE_DEBOUNCE_MS
                        )

                        if (duplicate != null) {
                            Log.d("BankNotificationListener", "Skipping duplicate notification: $rawTextVal")
                            return@withLock
                        }

                        // Check if matching account exists by currency or card
                        val accounts = db.accountDao().getActiveAccountsSync()
                        val matchedAccount = accounts.find { acc ->
                            (parsed.cardLast4 != null && acc.name.contains(parsed.cardLast4)) ||
                                    acc.name.contains(parsed.bankName, ignoreCase = true) ||
                                    (acc.currency == parsed.currency && !acc.isArchived)
                        } ?: accounts.firstOrNull()

                        val categories = db.categoryDao().getAllCategoriesSync()
                        val suggestedCatId = BankNotificationParser.matchCategoryId(categories, parsed.matchedCategoryKeyword)

                        val isZenmoneyPush = packageName == "ru.zenmoney.androidsub" || packageName == "ru.zenmoney.android"
                        val zenmoneyCategory = if (isZenmoneyPush) parsed.cardLast4 else null
                        val actualCardLast4 = if (isZenmoneyPush) null else parsed.cardLast4
                        
                        val entity = PendingNotificationEntity(
                            packageName = packageName,
                            bankName = parsed.bankName,
                            rawText = if (isZenmoneyPush && zenmoneyCategory != null) "$zenmoneyCategory: $rawTextVal" else rawTextVal,
                            type = parsed.type,
                            amount = parsed.amount,
                            currency = parsed.currency,
                            merchantOrSender = parsed.merchant,
                            cardLast4 = actualCardLast4,
                            suggestedCategoryId = suggestedCatId,
                            suggestedAccountId = matchedAccount?.id,
                            timestamp = timestampVal
                        )
                        val insertedId = db.pendingNotificationDao().insertNotification(entity)

                        // Send push reminder with stable notification ID tied to inserted record ID
                        PushNotificationHelper.sendBankTransactionReminder(
                            context = applicationContext,
                            bankName = parsed.bankName,
                            amount = parsed.amount,
                            currency = parsed.currency,
                            merchant = if (isZenmoneyPush && zenmoneyCategory != null) "${parsed.merchant} (Заметка: $zenmoneyCategory)" else parsed.merchant,
                            type = parsed.type,
                            notificationId = (insertedId % 100000).toInt() + 1000
                        )
                    } catch (e: Exception) {
                        Log.e("BankNotificationListener", "Failed to process bank notification", e)
                    }
                }
            }
        }
    }
}
