package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.repository.BackupRepository
import com.example.service.UserFinancePreferences
import com.example.service.receipt.ReceiptFileManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptBackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var preferences: UserFinancePreferences
    private lateinit var fileManager: ReceiptFileManager
    private lateinit var backupRepository: BackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserFinancePreferences(context)
        fileManager = ReceiptFileManager(context)
        backupRepository = BackupRepository(
            context = context,
            appDatabase = db,
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            transactionDao = db.transactionDao(),
            budgetDao = db.budgetDao(),
            goalDao = db.goalDao(),
            debtDao = db.debtDao(),
            plannedTransactionDao = db.plannedTransactionDao(),
            preferences = preferences,
            receiptDao = db.receiptDao(),
            receiptFileManager = fileManager
        )
    }

    @After
    fun tearDown() {
        db.close()
        fileManager.receiptsDir.deleteRecursively()
        fileManager.tempDir.deleteRecursively()
    }

    @Test
    fun testExportAndRestoreReceiptWithItemsAndPhoto() = runBlocking {
        // 1. Setup account and transaction
        val accId = db.accountDao().insertAccount(
            AccountEntity(id = 1L, name = "Основной", type = "DEBIT", balance = 50000.0)
        )
        val catId = db.categoryDao().insertCategory(
            CategoryEntity(id = 1L, name = "Супермаркеты", type = "EXPENSE", iconName = "shopping_cart", colorHex = "#FF5722")
        )
        val txnId = db.transactionDao().insertTransaction(
            TransactionEntity(id = 100L, type = "EXPENSE", amount = 1247.0, accountId = accId, categoryId = catId)
        )

        // 2. Create physical dummy photo file in receiptsDir
        val photoFile = fileManager.generateReceiptFile()
        photoFile.writeBytes("SAMPLE_IMAGE_RAW_BYTES_FOR_RECEIPT".toByteArray(Charsets.UTF_8))

        // 3. Save receipt and items
        val receiptEntity = ReceiptEntity(
            id = 10L,
            transactionId = txnId,
            imagePath = photoFile.absolutePath,
            merchant = "Пятёрочка",
            dateTime = 1727189000000L,
            total = 1247.0,
            fiscalNumber = "9960440300123456",
            fiscalDocument = "12345",
            fiscalSign = "9876543210",
            operationType = "Приход",
            rawQrData = "t=20260924T1842&s=1247.00&fn=9960440300123456&i=12345&fp=9876543210&n=1"
        )
        val items = listOf(
            ReceiptItemEntity(id = 1L, receiptId = 10L, name = "Хлеб", quantity = 1.0, price = 89.0, total = 89.0),
            ReceiptItemEntity(id = 2L, receiptId = 10L, name = "Молоко", quantity = 1.0, price = 109.0, total = 109.0)
        )
        db.receiptDao().saveReceiptWithItems(receiptEntity, items)

        // 4. Create backup
        val backupData = backupRepository.createBackupData()

        // Verify exported backup data
        assertEquals(1, backupData.receipts.size)
        assertEquals(2, backupData.receiptItems.size)
        assertEquals(1, backupData.receiptPhotos.size)

        val exportedReceipt = backupData.receipts.first()
        assertEquals("Пятёрочка", exportedReceipt.merchant)
        assertEquals(1247.0, exportedReceipt.total ?: 0.0, 0.01)
        // Path in backup must be relative (independent of device filesystem)
        assertTrue(exportedReceipt.imagePath?.startsWith("receipts/") == true)

        val exportedPhoto = backupData.receiptPhotos.first()
        assertEquals(photoFile.name, exportedPhoto.fileName)
        assertTrue(exportedPhoto.base64Data.isNotBlank())

        // 5. Clear current database and file system to simulate fresh device restore
        photoFile.delete()
        db.receiptDao().deleteAllReceiptItems()
        db.receiptDao().deleteAllReceipts()
        db.transactionDao().deleteAllTransactions()
        db.accountDao().deleteAllAccounts()

        assertEquals(0, db.receiptDao().getAllReceiptsSync().size)
        assertEquals(0, db.transactionDao().getAllTransactionsSync().size)

        // 6. Restore from backup
        backupRepository.restoreBackupData(backupData)

        // 7. Verify restored database records and relationships
        val restoredTxn = db.transactionDao().getAllTransactionsSync().find { it.id == txnId }
        assertNotNull(restoredTxn)

        val restoredReceiptWithItems = db.receiptDao().getReceiptByTransactionIdSync(txnId)
        assertNotNull(restoredReceiptWithItems)
        val restoredReceipt = restoredReceiptWithItems!!.receipt
        val restoredItems = restoredReceiptWithItems.items

        assertEquals("Пятёрочка", restoredReceipt.merchant)
        assertEquals(1247.0, restoredReceipt.total ?: 0.0, 0.01)
        assertEquals("9960440300123456", restoredReceipt.fiscalNumber)
        assertEquals(2, restoredItems.size)
        assertEquals("Хлеб", restoredItems[0].name)
        assertEquals("Молоко", restoredItems[1].name)

        // 8. Verify restored photo file
        assertNotNull(restoredReceipt.imagePath)
        val restoredFile = File(restoredReceipt.imagePath!!)
        assertTrue(restoredFile.exists())
        assertEquals("SAMPLE_IMAGE_RAW_BYTES_FOR_RECEIPT", restoredFile.readText(Charsets.UTF_8))
    }

    @Test
    fun testCorruptedBackupWithOrphanReceiptThrowsException() = runBlocking {
        val corruptedBackup = BackupData(
            timestamp = System.currentTimeMillis(),
            accounts = listOf(AccountEntity(id = 1L, name = "Счёт", type = "DEBIT", balance = 100.0)),
            categories = listOf(CategoryEntity(id = 1L, name = "Еда", type = "EXPENSE", iconName = "fastfood", colorHex = "#000000")),
            transactions = listOf(TransactionEntity(id = 100L, type = "EXPENSE", amount = 50.0, accountId = 1L)),
            budgets = emptyList(),
            goals = emptyList(),
            debts = emptyList(),
            plannedTransactions = emptyList(),
            preferences = BackupPreferences(
                paydayDay = 10,
                bankOfTheMonth = "VTB",
                pushNotificationsEnabled = true,
                themeMode = "SYSTEM",
                eveningSummaryEnabled = false,
                eveningSummaryTime = "21:00",
                bankPushInterceptEnabled = true,
                zenmoneyPushInterceptEnabled = false,
                spamKeywords = emptyList()
            ),
            // Orphan receipt pointing to non-existent transactionId 999
            receipts = listOf(
                ReceiptEntity(id = 1L, transactionId = 999L, merchant = "Магнит", total = 50.0)
            )
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            backupRepository.validateBackupData(corruptedBackup)
        }
        assertTrue(exception.message?.contains("не привязанные к операциям") == true)
    }

    @Test
    fun testLegacyBackupWithoutReceiptsRestoresSuccessfully() = runBlocking {
        val legacyBackup = BackupData(
            timestamp = System.currentTimeMillis(),
            accounts = listOf(AccountEntity(id = 1L, name = "Основной", type = "DEBIT", balance = 1000.0)),
            categories = listOf(CategoryEntity(id = 1L, name = "Кафе", type = "EXPENSE", iconName = "local_cafe", colorHex = "#000000")),
            transactions = listOf(TransactionEntity(id = 1L, type = "EXPENSE", amount = 200.0, accountId = 1L)),
            budgets = emptyList(),
            goals = emptyList(),
            debts = emptyList(),
            plannedTransactions = emptyList(),
            preferences = BackupPreferences(
                paydayDay = 10,
                bankOfTheMonth = "VTB",
                pushNotificationsEnabled = true,
                themeMode = "SYSTEM",
                eveningSummaryEnabled = false,
                eveningSummaryTime = "21:00",
                bankPushInterceptEnabled = true,
                zenmoneyPushInterceptEnabled = false,
                spamKeywords = emptyList()
            ),
            receipts = emptyList(),
            receiptItems = emptyList(),
            receiptPhotos = emptyList()
        )

        backupRepository.restoreBackupData(legacyBackup)

        assertEquals(1, db.accountDao().getAllAccountsSync().size)
        assertEquals(1, db.transactionDao().getAllTransactionsSync().size)
        assertEquals(0, db.receiptDao().getAllReceiptsSync().size)
    }
}
