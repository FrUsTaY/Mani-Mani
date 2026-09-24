package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.ReceiptEntity
import com.example.data.entity.ReceiptItemEntity
import com.example.data.entity.ReceiptWithItems
import com.example.service.receipt.ReceiptFileManager
import com.example.service.receipt.ReceiptPreferenceManager
import com.example.ui.screens.receipt.ReceiptTabContent
import com.example.ui.util.CurrencyHelper
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptDataDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var fileManager: ReceiptFileManager
    private lateinit var preferenceManager: ReceiptPreferenceManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = ReceiptFileManager(context)
        preferenceManager = ReceiptPreferenceManager(context)
    }

    @After
    fun tearDown() {
        fileManager.receiptsDir.deleteRecursively()
        fileManager.tempDir.deleteRecursively()
    }

    @Test
    fun testMerchantDisplay_withValidMerchant_andWithFallback() {
        val receiptWithMerchant = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 1,
                transactionId = 100,
                merchant = "ООО «Ашан»",
                total = 500.0
            )
        )

        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReceiptTabContent(
                    receiptWithItems = receiptWithMerchant,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    onAttachPhoto = {},
                    onDeletePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("receipt_merchant_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_merchant_name").assertTextEquals("ООО «Ашан»")
    }

    @Test
    fun testDateDisplay_formatsTimestampCorrectly() {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2026, Calendar.SEPTEMBER, 24, 18, 42, 0)
        val timestamp = cal.timeInMillis

        val receiptWithDate = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 2,
                transactionId = 101,
                merchant = "Пятёрочка",
                dateTime = timestamp,
                total = 1247.0
            )
        )

        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReceiptTabContent(
                    receiptWithItems = receiptWithDate,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    onAttachPhoto = {},
                    onDeletePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("receipt_date_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_date_text").assertTextContains("24.09.2026 18:42")
    }

    @Test
    fun testTotalAmountDisplay() {
        val receiptWithTotal = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 3,
                transactionId = 102,
                total = 1247.0
            )
        )

        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReceiptTabContent(
                    receiptWithItems = receiptWithTotal,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    onAttachPhoto = {},
                    onDeletePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("receipt_total_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_total_amount").assertTextContains(CurrencyHelper.formatAmount(1247.0, "RUB"))
    }

    @Test
    fun testFiscalDetailsDisplay_showsFnFdFpAndOperationType() {
        val receiptWithFiscal = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 4,
                transactionId = 103,
                total = 850.0,
                fiscalNumber = "9960440300123456",
                fiscalDocument = "45678",
                fiscalSign = "1234567890",
                operationType = "Приход"
            )
        )

        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReceiptTabContent(
                    receiptWithItems = receiptWithFiscal,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    onAttachPhoto = {},
                    onDeletePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("receipt_fiscal_details").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_fn_value").assertTextEquals("9960440300123456")
        composeTestRule.onNodeWithTag("receipt_fd_value").assertTextEquals("45678")
        composeTestRule.onNodeWithTag("receipt_fp_value").assertTextEquals("1234567890")
        composeTestRule.onNodeWithTag("receipt_operation_type_value").assertTextEquals("Приход")
    }

    @Test
    fun testItemsListDisplay_showsItemNamesQuantitiesPricesAndTotals() {
        val receiptWithItems = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 5,
                transactionId = 104,
                total = 647.0
            ),
            items = listOf(
                ReceiptItemEntity(id = 1, receiptId = 5, name = "Хлеб Бородинский", quantity = 1.0, price = 89.0, total = 89.0),
                ReceiptItemEntity(id = 2, receiptId = 5, name = "Молоко 3.2%", quantity = 2.0, price = 109.0, total = 218.0),
                ReceiptItemEntity(id = 3, receiptId = 5, name = "Сыр Гауда", quantity = 1.0, price = 340.0, total = 340.0)
            )
        )

        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReceiptTabContent(
                    receiptWithItems = receiptWithItems,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    onAttachPhoto = {},
                    onDeletePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("receipt_items_toggle").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_items_list").assertExists()

        // Check item 0
        composeTestRule.onNodeWithTag("receipt_item_name_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_item_name_0").assertTextEquals("Хлеб Бородинский")
        composeTestRule.onNodeWithTag("receipt_item_total_0").assertTextContains(CurrencyHelper.formatAmount(89.0, "RUB"))

        // Check item 1
        composeTestRule.onNodeWithTag("receipt_item_name_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_item_name_1").assertTextEquals("Молоко 3.2%")
        composeTestRule.onNodeWithTag("receipt_item_total_1").assertTextContains(CurrencyHelper.formatAmount(218.0, "RUB"))

        // Check item 2
        composeTestRule.onNodeWithTag("receipt_item_name_2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_item_name_2").assertTextEquals("Сыр Гауда")
        composeTestRule.onNodeWithTag("receipt_item_total_2").assertTextContains(CurrencyHelper.formatAmount(340.0, "RUB"))
    }

    @Test
    fun testAmountComparison_whenAmountsMatch_showsSuccessBadge() {
        val receipt = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 6,
                transactionId = 105,
                total = 1247.0
            )
        )

        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReceiptTabContent(
                    receiptWithItems = receipt,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    transactionAmount = -1247.0, // Expense amount
                    onAttachPhoto = {},
                    onDeletePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("receipt_sum_match").assertIsDisplayed()
        composeTestRule.onNodeWithText("✓ Сумма операции совпадает с чеком").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_sum_diff").assertDoesNotExist()
    }

    @Test
    fun testAmountComparison_whenAmountsDiffer_showsWarningBadgeWithBothAmounts() {
        val receipt = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 7,
                transactionId = 106,
                total = 1297.0
            )
        )

        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReceiptTabContent(
                    receiptWithItems = receipt,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    transactionAmount = -1247.0, // Expense amount is 1247, receipt is 1297
                    onAttachPhoto = {},
                    onDeletePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("receipt_sum_diff").assertIsDisplayed()
        composeTestRule.onNodeWithText("⚠ Сумма операции отличается от суммы чека").assertIsDisplayed()
        composeTestRule.onNodeWithText("Операция: 1 247 ₽", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Чек: 1 297 ₽", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_sum_match").assertDoesNotExist()
    }
}
