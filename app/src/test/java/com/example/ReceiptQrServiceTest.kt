package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.AccountEntity
import com.example.data.entity.ReceiptEntity
import com.example.data.entity.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.service.receipt.ReceiptQrService
import com.example.service.receipt.api.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReceiptQrServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var fakeApi: FakeProverkaChekaApi
    private lateinit var service: ReceiptQrService
    private val testDispatcher = StandardTestDispatcher()

    private var testTxId: Long = 0L

    class FakeProverkaChekaApi : ProverkaChekaApi {
        var responseToReturn: Response<ProverkaChekaResponse>? = null
        var shouldThrowNetworkError: Boolean = false
        var lastCapturedToken: String? = null
        var lastCapturedQrraw: String? = null

        override suspend fun getCheck(token: String, qrraw: String): Response<ProverkaChekaResponse> {
            lastCapturedToken = token
            lastCapturedQrraw = qrraw
            if (shouldThrowNetworkError) {
                throw java.io.IOException("Connection refused")
            }
            return responseToReturn ?: Response.success(ProverkaChekaResponse(code = 1))
        }
    }

    @Before
    fun setup() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(db)
        fakeApi = FakeProverkaChekaApi()
        service = ReceiptQrService(repository, fakeApi, testDispatcher)

        // Seed account and transaction
        val accId = db.accountDao().insertAccount(AccountEntity(id = 1, name = "Основной", type = "DEBIT", balance = 10000.0))
        testTxId = db.transactionDao().insertTransaction(
            TransactionEntity(
                accountId = accId,
                amount = 1247.0,
                type = "EXPENSE",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun processAndSaveReceiptQr_success_savesReceiptAndItems_andPreservesPhoto() = runTest(testDispatcher) {
        // Arrange: Transaction already has a photo attached
        val existingPhotoPath = "/data/user/0/app/receipt_123.jpg"
        repository.insertReceipt(
            ReceiptEntity(
                transactionId = testTxId,
                imagePath = existingPhotoPath
            )
        )

        val apiResponse = ProverkaChekaResponse(
            code = 1,
            data = ProverkaChekaData(
                json = FnsReceiptJson(
                    user = "ООО «Агроторг»",
                    retailPlace = "Пятёрочка",
                    dateTime = "2023-05-12T17:59:00",
                    totalSum = 124700.0, // 1247.00 rubles in kopecks
                    fiscalDriveNumber = "9287440300647312",
                    fiscalDocumentNumber = 34873,
                    fiscalSign = 3526148825,
                    operationType = 1,
                    items = listOf(
                        FnsReceiptItemJson(name = "Хлеб Бородинский", price = 8900.0, quantity = 1.0, sum = 8900.0),
                        FnsReceiptItemJson(name = "Молоко 3.2%", price = 10900.0, quantity = 2.0, sum = 21800.0)
                    )
                )
            )
        )
        fakeApi.responseToReturn = Response.success(apiResponse)

        val qrRaw = "t=20201014T1849&s=1247.00&fn=9287440300647312&i=34873&fp=3526148825&n=1"

        // Act
        val result = service.processAndSaveReceiptQr(testTxId, qrRaw, "valid_token")

        // Assert
        assertTrue(result.isSuccess)
        val receiptWithItems = result.getOrNull()
        assertNotNull(receiptWithItems)

        val receipt = receiptWithItems!!.receipt
        assertEquals("Пятёрочка", receipt.merchant)
        assertEquals(1247.0, receipt.total!!, 0.01)
        assertEquals("9287440300647312", receipt.fiscalNumber)
        assertEquals("34873", receipt.fiscalDocument)
        assertEquals("3526148825", receipt.fiscalSign)
        assertEquals("Приход", receipt.operationType)
        // Check photo is strictly preserved!
        assertEquals(existingPhotoPath, receipt.imagePath)

        // Check items
        assertEquals(2, receiptWithItems.items.size)
        val item1 = receiptWithItems.items[0]
        assertEquals("Хлеб Бородинский", item1.name)
        assertEquals(89.0, item1.price, 0.01)
        assertEquals(89.0, item1.total, 0.01)
        assertEquals(1.0, item1.quantity, 0.01)

        val item2 = receiptWithItems.items[1]
        assertEquals("Молоко 3.2%", item2.name)
        assertEquals(109.0, item2.price, 0.01)
        assertEquals(218.0, item2.total, 0.01)
        assertEquals(2.0, item2.quantity, 0.01)
    }

    @Test
    fun processAndSaveReceiptQr_invalidQr_returnsFailure() = runTest(testDispatcher) {
        val result = service.processAndSaveReceiptQr(testTxId, "not_a_receipt", "my_token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun processAndSaveReceiptQr_missingApiKey_returnsFailure() = runTest(testDispatcher) {
        val validQr = "t=20201014T1849&s=1247.00&fn=9287440300647312&i=34873&fp=3526148825&n=1"
        val result = service.processAndSaveReceiptQr(testTxId, validQr, "")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun processAndSaveReceiptQr_checkNotFoundInFns_returnsFailureWithMessage() = runTest(testDispatcher) {
        fakeApi.responseToReturn = Response.success(ProverkaChekaResponse(code = 0))
        val validQr = "t=20201014T1849&s=1247.00&fn=9287440300647312&i=34873&fp=3526148825&n=1"

        val result = service.processAndSaveReceiptQr(testTxId, validQr, "token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("базу ФНС"))
    }

    @Test
    fun processAndSaveReceiptQr_invalidTokenFromApi_returnsFailure() = runTest(testDispatcher) {
        fakeApi.responseToReturn = Response.success(ProverkaChekaResponse(code = 3, message = "Неверный токен"))
        val validQr = "t=20201014T1849&s=1247.00&fn=9287440300647312&i=34873&fp=3526148825&n=1"

        val result = service.processAndSaveReceiptQr(testTxId, validQr, "bad_token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("API-ключ"))
    }

    @Test
    fun processAndSaveReceiptQr_networkError_returnsFailure() = runTest(testDispatcher) {
        fakeApi.shouldThrowNetworkError = true
        val validQr = "t=20201014T1849&s=1247.00&fn=9287440300647312&i=34873&fp=3526148825&n=1"

        val result = service.processAndSaveReceiptQr(testTxId, validQr, "token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("подключение к интернету"))
    }
}
