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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptTabUiTest {

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
        preferenceManager.clearApiKey()
    }

    @After
    fun tearDown() {
        fileManager.receiptsDir.deleteRecursively()
        fileManager.tempDir.deleteRecursively()
        preferenceManager.clearApiKey()
    }

    @Test
    fun testEmptyReceiptDisplaysActionButtons() {
        composeTestRule.setContent {
            ReceiptTabContent(
                receiptWithItems = null,
                fileManager = fileManager,
                preferenceManager = preferenceManager,
                onAttachPhoto = {},
                onDeletePhoto = {},
                onDeleteQrData = {},
                onProcessQrScanned = {}
            )
        }

        composeTestRule.onNodeWithTag("camera_capture_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_picker_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_qr_button").assertIsDisplayed()
    }

    @Test
    fun testExistingPhotoDisplaysPreviewAndActionButtons() {
        val testFile = fileManager.generateReceiptFile().apply {
            writeText("dummy receipt")
        }
        val receiptWithItems = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 1,
                transactionId = 10,
                imagePath = testFile.absolutePath
            )
        )

        var deletePhotoCalled = false

        composeTestRule.setContent {
            ReceiptTabContent(
                receiptWithItems = receiptWithItems,
                fileManager = fileManager,
                preferenceManager = preferenceManager,
                onAttachPhoto = {},
                onDeletePhoto = { deletePhotoCalled = true },
                onDeleteQrData = {},
                onProcessQrScanned = {}
            )
        }

        composeTestRule.onNodeWithTag("open_photo_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_photo_button").assertIsDisplayed().performClick()

        // Confirmation dialog appears
        composeTestRule.onNodeWithTag("confirm_delete_photo_button").assertIsDisplayed().performClick()

        assertTrue(deletePhotoCalled)
    }

    @Test
    fun testQrDataDisplaysFnsBadgeAndItems_andDeleteQrConfirmation() {
        val receiptWithItems = ReceiptWithItems(
            receipt = ReceiptEntity(
                id = 1,
                transactionId = 10,
                merchant = "Магнит",
                total = 1247.0,
                fiscalDocument = "12345",
                fiscalSign = "987654"
            ),
            items = listOf(
                ReceiptItemEntity(id = 1, receiptId = 1, name = "Сыр Российский", quantity = 1.0, price = 247.0, total = 247.0),
                ReceiptItemEntity(id = 2, receiptId = 1, name = "Кофе зерновой", quantity = 1.0, price = 1000.0, total = 1000.0)
            )
        )

        var deleteQrCalled = false

        composeTestRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ReceiptTabContent(
                    receiptWithItems = receiptWithItems,
                    fileManager = fileManager,
                    preferenceManager = preferenceManager,
                    transactionAmount = -1247.0,
                    onAttachPhoto = {},
                    onDeletePhoto = {},
                    onDeleteQrData = { deleteQrCalled = true },
                    onProcessQrScanned = {}
                )
            }
        }

        // FNS badge displayed
        composeTestRule.onNodeWithTag("fns_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Магнит").assertIsDisplayed()
        composeTestRule.onNodeWithText("✓ Сумма операции совпадает с чеком").assertIsDisplayed()

        // Delete QR
        composeTestRule.onNodeWithTag("delete_qr_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("confirm_delete_qr_button").assertIsDisplayed().performClick()

        assertTrue(deleteQrCalled)
    }

    @Test
    fun testScanQr_whenApiKeyNotConfigured_opensApiKeyDialog() {
        composeTestRule.setContent {
            ReceiptTabContent(
                receiptWithItems = null,
                fileManager = fileManager,
                preferenceManager = preferenceManager,
                onAttachPhoto = {},
                onDeletePhoto = {},
                onDeleteQrData = {},
                onProcessQrScanned = {}
            )
        }

        // Tap scan QR without API key configured
        composeTestRule.onNodeWithTag("scan_qr_button").performClick()

        // API Key Dialog should appear
        composeTestRule.onNodeWithTag("receipt_api_key_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_receipt_api_key_button").assertIsDisplayed()
    }
}
