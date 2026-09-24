package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.dao.*
import com.example.data.entity.*
import com.example.service.UserFinancePreferences
import com.example.service.yandex.YandexDiskApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import java.io.InputStream
import java.io.OutputStream

class BackupRepository(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val debtDao: DebtDao,
    private val plannedTransactionDao: PlannedTransactionDao,
    private val preferences: UserFinancePreferences,
    private val receiptDao: ReceiptDao = appDatabase.receiptDao(),
    private val receiptFileManager: com.example.service.receipt.ReceiptFileManager = com.example.service.receipt.ReceiptFileManager(context)
) {
    private val moshi = Moshi.Builder().build()
    
    @OptIn(ExperimentalStdlibApi::class)
    private val backupAdapter = moshi.adapter<BackupData>()

    private val yandexApi: YandexDiskApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://cloud-api.yandex.net/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YandexDiskApi::class.java)
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } else {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getDecoder().decode(str)
        } else {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        }
    }

    suspend fun createBackupData(): BackupData = withContext(Dispatchers.IO) {
        val receipts = receiptDao.getAllReceiptsSync()
        val receiptItems = receiptDao.getAllReceiptItemsSync()

        // Prepare photo files for export (Section 24 & 26 ТЗ)
        val receiptPhotos = mutableListOf<BackupReceiptPhoto>()
        val sanitizedReceipts = receipts.map { receipt ->
            val imgPath = receipt.imagePath
            if (!imgPath.isNullOrBlank()) {
                val file = java.io.File(imgPath)
                if (file.exists() && file.isFile) {
                    try {
                        val bytes = file.readBytes()
                        val base64 = encodeBase64(bytes)
                        val fileName = file.name
                        receiptPhotos.add(BackupReceiptPhoto(fileName = fileName, base64Data = base64))
                        // In backup JSON, path is relative and independent of Android absolute path
                        receipt.copy(imagePath = "receipts/$fileName")
                    } catch (_: Exception) {
                        receipt.copy(imagePath = null)
                    }
                } else {
                    receipt.copy(imagePath = null)
                }
            } else {
                receipt
            }
        }

        BackupData(
            timestamp = System.currentTimeMillis(),
            accounts = accountDao.getAllAccountsSync(),
            categories = categoryDao.getAllCategoriesSync(),
            transactions = transactionDao.getAllTransactionsSync(),
            budgets = budgetDao.getAllBudgetsSync(),
            goals = goalDao.getAllGoalsSync(),
            debts = debtDao.getAllDebtsSync(),
            plannedTransactions = plannedTransactionDao.getAllPlannedTransactionsSync(),
            preferences = BackupPreferences(
                paydayDay = preferences.getPaydayDay(),
                bankOfTheMonth = preferences.getBankOfTheMonth(),
                pushNotificationsEnabled = preferences.isPushNotificationsEnabled(),
                themeMode = preferences.getThemeMode().name,
                eveningSummaryEnabled = preferences.isEveningSummaryEnabled(),
                eveningSummaryTime = preferences.getEveningSummaryTime(),
                bankPushInterceptEnabled = preferences.isBankPushInterceptEnabled(),
                zenmoneyPushInterceptEnabled = preferences.isZenmoneyPushInterceptEnabled(),
                spamKeywords = preferences.getSpamKeywords().toList()
            ),
            receipts = sanitizedReceipts,
            receiptItems = receiptItems,
            receiptPhotos = receiptPhotos
        )
    }

    fun validateBackupData(backup: BackupData) {
        if (backup.accounts.isEmpty()) {
            throw IllegalStateException("Файл бэкапа поврежден или не содержит счетов. База данных сохранена в неизменном виде.")
        }
        if (backup.accounts.any { it.name.isBlank() }) {
            throw IllegalStateException("Файл бэкапа поврежден: обнаружены счета с пустыми названиями. Откат изменений.")
        }
        if (backup.categories.any { it.name.isBlank() }) {
            throw IllegalStateException("Файл бэкапа поврежден: обнаружены категории с пустыми названиями. Откат изменений.")
        }
        // Section 25 & 36 ТЗ: Integrity check — ensure receipts belong to transactions in this backup
        val transactionIds = backup.transactions.map { it.id }.toSet()
        val orphanReceipts = backup.receipts.filter { it.transactionId !in transactionIds }
        if (orphanReceipts.isNotEmpty()) {
            throw IllegalStateException("Файл бэкапа поврежден: обнаружены чеки, не привязанные к операциям. Откат изменений.")
        }
    }

    suspend fun restoreBackupData(backup: BackupData) = withContext(Dispatchers.IO) {
        // 1. Dry-run validation before touching database
        validateBackupData(backup)

        // 2. Atomic Room transaction (all-or-nothing rollback on any failure)
        appDatabase.withTransaction {
            // Clear old receipts and receipt items
            receiptDao.deleteAllReceiptItems()
            receiptDao.deleteAllReceipts()

            // Clear old financial data
            accountDao.deleteAllAccounts()
            categoryDao.deleteAllCategories()
            transactionDao.deleteAllTransactions()
            budgetDao.deleteAllBudgets()
            goalDao.deleteAllGoals()
            debtDao.deleteAllDebts()
            plannedTransactionDao.deleteAllPlannedTransactions()

            // Insert restored financial entities
            backup.accounts.forEach { accountDao.insertAccount(it) }
            categoryDao.insertCategories(backup.categories)
            backup.transactions.forEach { transactionDao.insertTransaction(it) }
            backup.budgets.forEach { budgetDao.insertBudget(it) }
            backup.goals.forEach { goalDao.insertGoal(it) }
            backup.debts.forEach { debtDao.insertDebt(it) }
            backup.plannedTransactions.forEach { plannedTransactionDao.insertPlannedTransaction(it) }

            // Restore photos to internal receipts directory (Section 25 & 26 ТЗ)
            val restoredPhotosMap = mutableMapOf<String, String>()
            val receiptsDir = receiptFileManager.receiptsDir
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs()
            }

            backup.receiptPhotos.forEach { photo ->
                try {
                    val bytes = decodeBase64(photo.base64Data)
                    val safeName = java.io.File(photo.fileName).name.takeIf { it.isNotBlank() }
                        ?: "receipt_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
                    val targetFile = java.io.File(receiptsDir, safeName)
                    targetFile.writeBytes(bytes)
                    restoredPhotosMap[safeName] = targetFile.absolutePath
                    restoredPhotosMap["receipts/$safeName"] = targetFile.absolutePath
                } catch (_: Exception) {}
            }

            // Restore Receipts with regenerated local absolute paths
            backup.receipts.forEach { receipt ->
                val newLocalPath = receipt.imagePath?.let { path ->
                    val cleanName = java.io.File(path).name
                    restoredPhotosMap[cleanName] ?: restoredPhotosMap[path]
                }
                val receiptToInsert = receipt.copy(imagePath = newLocalPath)
                receiptDao.insertReceipt(receiptToInsert)
            }

            // Restore ReceiptItems
            if (backup.receiptItems.isNotEmpty()) {
                receiptDao.insertReceiptItems(backup.receiptItems)
            }

            // Restore preferences
            preferences.setPaydayDay(backup.preferences.paydayDay)
            preferences.setBankOfTheMonth(backup.preferences.bankOfTheMonth)
            preferences.setPushNotificationsEnabled(backup.preferences.pushNotificationsEnabled)
            try {
                preferences.setThemeMode(com.example.service.AppThemeMode.valueOf(backup.preferences.themeMode))
            } catch (e: Exception) {}
            preferences.setEveningSummaryEnabled(backup.preferences.eveningSummaryEnabled)
            preferences.setEveningSummaryTime(backup.preferences.eveningSummaryTime)
            preferences.setBankPushInterceptEnabled(backup.preferences.bankPushInterceptEnabled)
            preferences.setZenmoneyPushInterceptEnabled(backup.preferences.zenmoneyPushInterceptEnabled)
            preferences.setSpamKeywords(backup.preferences.spamKeywords.toSet())
        }
    }

    suspend fun exportLocal(uri: Uri) = withContext(Dispatchers.IO) {
        val backupData = createBackupData()
        val json = backupAdapter.indent("  ").toJson(backupData)
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("Не удалось открыть файл для записи")
    }

    suspend fun importLocal(uri: Uri) = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw Exception("Не удалось прочитать файл")
        val backupData = backupAdapter.fromJson(json) ?: throw Exception("Неверный формат данных")
        restoreBackupData(backupData)
    }

    suspend fun exportCloud(token: String) = withContext(Dispatchers.IO) {
        if (token.isBlank()) throw Exception("API ключ Яндекс.Диска не указан")
        val authHeader = "OAuth $token"
        val path = "disk:/Приложения/ManiMani/manimani_backup.json"
        
        val backupData = createBackupData()
        val json = backupAdapter.indent("  ").toJson(backupData)
        
        // Ensure folder exists conceptually by using app:/ or just write if API allows.
        // If not, we might need to create the folder. Let's just try uploading to a flat path first
        // disk:/manimani_backup.json to avoid folder creation issues.
        val simplePath = "disk:/manimani_backup.json"
        
        val uploadInfo = yandexApi.getUploadUrl(authHeader, simplePath, overwrite = true)
        
        val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
        yandexApi.uploadFile(uploadInfo.href, requestBody)
    }

    suspend fun importCloud(token: String) = withContext(Dispatchers.IO) {
        if (token.isBlank()) throw Exception("API ключ Яндекс.Диска не указан")
        val authHeader = "OAuth $token"
        val simplePath = "disk:/manimani_backup.json"
        
        val downloadInfo = yandexApi.getDownloadUrl(authHeader, simplePath)
        
        val request = Request.Builder().url(downloadInfo.href).build()
        val response = OkHttpClient().newCall(request).execute()
        
        if (!response.isSuccessful) throw Exception("Ошибка загрузки файла с Я.Диска")
        val json = response.body?.string() ?: throw Exception("Пустой файл")
        
        val backupData = backupAdapter.fromJson(json) ?: throw Exception("Неверный формат данных")
        restoreBackupData(backupData)
    }
}
