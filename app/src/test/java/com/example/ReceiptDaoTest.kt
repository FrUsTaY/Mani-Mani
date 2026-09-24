package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieveReceiptWithItems() = runBlocking {
        val accId = db.accountDao().insertAccount(
            AccountEntity(name = "Card", type = "DEBIT", balance = 10000.0)
        )
        val txnId = db.transactionDao().insertTransaction(
            TransactionEntity(type = "EXPENSE", amount = 1247.0, accountId = accId)
        )

        val receipt = ReceiptEntity(
            transactionId = txnId,
            imagePath = "/data/user/0/receipt_123.jpg",
            merchant = "Пятёрочка",
            total = 1247.0,
            fiscalNumber = "9999078900000000",
            fiscalDocument = "12345",
            fiscalSign = "9876543210",
            operationType = "1",
            rawQrData = "t=20260924T1842&s=1247.00&fn=9999078900000000&i=12345&fp=9876543210&n=1"
        )

        val items = listOf(
            ReceiptItemEntity(receiptId = 0, name = "Хлеб", quantity = 1.0, price = 89.0, total = 89.0),
            ReceiptItemEntity(receiptId = 0, name = "Молоко", quantity = 2.0, price = 109.0, total = 218.0),
            ReceiptItemEntity(receiptId = 0, name = "Курица", quantity = 1.0, price = 940.0, total = 940.0)
        )

        val receiptId = repository.saveReceiptWithItems(receipt, items)
        assertTrue(receiptId > 0)

        val retrieved = repository.getReceiptByTransactionId(txnId).first()
        assertNotNull(retrieved)
        assertEquals("Пятёрочка", retrieved!!.receipt.merchant)
        assertEquals("/data/user/0/receipt_123.jpg", retrieved.receipt.imagePath)
        assertEquals(1247.0, retrieved.receipt.total!!, 0.01)
        assertEquals(3, retrieved.items.size)
        assertEquals("Хлеб", retrieved.items[0].name)
        assertEquals("Молоко", retrieved.items[1].name)
        assertEquals("Курица", retrieved.items[2].name)
    }

    @Test
    fun testIndependentDeletionOfPhotoAndQrData() = runBlocking {
        val accId = db.accountDao().insertAccount(
            AccountEntity(name = "Card", type = "DEBIT", balance = 10000.0)
        )
        val txnId = db.transactionDao().insertTransaction(
            TransactionEntity(type = "EXPENSE", amount = 500.0, accountId = accId)
        )

        val receipt = ReceiptEntity(
            transactionId = txnId,
            imagePath = "/data/user/0/temp_photo.jpg",
            merchant = "Магнит",
            total = 500.0
        )
        val items = listOf(
            ReceiptItemEntity(receiptId = 0, name = "Сок", quantity = 1.0, price = 500.0, total = 500.0)
        )
        repository.saveReceiptWithItems(receipt, items)

        // 1. Delete Photo only -> QR data should remain
        val oldPhoto = repository.deleteReceiptPhoto(txnId)
        assertEquals("/data/user/0/temp_photo.jpg", oldPhoto)

        val afterPhotoDelete = repository.getReceiptByTransactionIdSync(txnId)
        assertNotNull(afterPhotoDelete)
        assertNull(afterPhotoDelete!!.receipt.imagePath)
        assertEquals("Магнит", afterPhotoDelete.receipt.merchant)
        assertEquals(1, afterPhotoDelete.items.size)

        // 2. Add photo back
        repository.updateReceipt(afterPhotoDelete.receipt.copy(imagePath = "/data/user/0/new_photo.jpg"))

        // 3. Delete QR data only -> Photo should remain
        repository.deleteReceiptQrData(txnId)
        val afterQrDelete = repository.getReceiptByTransactionIdSync(txnId)
        assertNotNull(afterQrDelete)
        assertEquals("/data/user/0/new_photo.jpg", afterQrDelete!!.receipt.imagePath)
        assertNull(afterQrDelete.receipt.merchant)
        assertNull(afterQrDelete.receipt.total)
        assertTrue(afterQrDelete.items.isEmpty())

        // 4. Finally delete photo when no QR data -> Entire receipt deleted
        repository.deleteReceiptPhoto(txnId)
        val afterAllDelete = repository.getReceiptByTransactionIdSync(txnId)
        assertNull(afterAllDelete)
    }

    @Test
    fun testCascadeDeletionWhenTransactionIsDeleted() = runBlocking {
        val accId = db.accountDao().insertAccount(
            AccountEntity(name = "Card", type = "DEBIT", balance = 10000.0)
        )
        val txn = TransactionEntity(type = "EXPENSE", amount = 300.0, accountId = accId)
        val txnId = db.transactionDao().insertTransaction(txn)
        val savedTxn = txn.copy(id = txnId)

        val receipt = ReceiptEntity(
            transactionId = txnId,
            imagePath = "/data/user/0/receipt_cascade.jpg",
            merchant = "Аптека",
            total = 300.0
        )
        val items = listOf(
            ReceiptItemEntity(receiptId = 0, name = "Аспирин", quantity = 1.0, price = 300.0, total = 300.0)
        )
        repository.saveReceiptWithItems(receipt, items)

        // Check it exists
        assertNotNull(repository.getReceiptByTransactionIdSync(txnId))

        // Delete transaction
        repository.deleteTransaction(savedTxn)

        // Check transaction is deleted
        assertNull(db.transactionDao().getTransactionById(txnId))
        // Check receipt is deleted
        assertNull(repository.getReceiptByTransactionIdSync(txnId))
    }
}
