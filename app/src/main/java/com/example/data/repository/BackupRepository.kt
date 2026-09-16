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
import java.io.InputStream
import java.io.OutputStream

class BackupRepository(
    private val context: Context,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val debtDao: DebtDao,
    private val plannedTransactionDao: PlannedTransactionDao,
    private val preferences: UserFinancePreferences
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

    suspend fun createBackupData(): BackupData = withContext(Dispatchers.IO) {
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
                zenmoneyPushInterceptEnabled = preferences.isZenmoneyPushInterceptEnabled()
            )
        )
    }

    suspend fun restoreBackupData(backup: BackupData) = withContext(Dispatchers.IO) {
        // Clear old data
        accountDao.deleteAllAccounts()
        categoryDao.deleteAllCategories()
        transactionDao.deleteAllTransactions()
        budgetDao.deleteAllBudgets()
        goalDao.deleteAllGoals()
        debtDao.deleteAllDebts()
        plannedTransactionDao.deleteAllPlannedTransactions()

        // Insert new data
        // We use insert for all of them. For accounts/categories with predefined IDs from backup
        // the OnConflictStrategy.REPLACE will keep the IDs intact.
        backup.accounts.forEach { accountDao.insertAccount(it) }
        categoryDao.insertCategories(backup.categories)
        backup.transactions.forEach { transactionDao.insertTransaction(it) }
        backup.budgets.forEach { budgetDao.insertBudget(it) }
        backup.goals.forEach { goalDao.insertGoal(it) }
        backup.debts.forEach { debtDao.insertDebt(it) }
        backup.plannedTransactions.forEach { plannedTransactionDao.insertPlannedTransaction(it) }

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
