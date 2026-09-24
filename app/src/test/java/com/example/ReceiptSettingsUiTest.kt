package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.accounts.AccountsSettingsScreen
import com.example.ui.viewmodel.FinanceUiState
import com.example.ui.viewmodel.FinanceViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptSettingsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var viewModel: FinanceViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = FinanceViewModel(app)
        viewModel.clearReceiptApiKey()
    }

    @Test
    fun testReceiptSettings_notConfigured_displaysUnconfiguredState_andOpensDialog() {
        val state = FinanceUiState(
            isReceiptApiKeyConfigured = false,
            receiptApiKey = "",
            receiptApiKeyMasked = ""
        )

        var savedKey = ""

        composeTestRule.setContent {
            AccountsSettingsScreen(
                viewModel = viewModel,
                state = state,
                onAddAccountClick = {},
                onEditAccount = {},
                onArchiveAccount = {},
                onDeleteAccount = {},
                onCurrencyChange = {},
                onOpenBankSync = {},
                onOpenGeminiAssistant = {},
                onSaveGeminiApiKey = {},
                onTestGeminiApiKey = { _, _ -> },
                onClearGeminiApiKey = {},
                onSaveReceiptApiKey = { savedKey = it },
                onClearReceiptApiKey = {}
            )
        }

        // Card should exist
        composeTestRule.onNodeWithTag("accounts_settings_screen")
            .performScrollToNode(hasTestTag("receipt_settings_card"))
        composeTestRule.onNodeWithTag("receipt_settings_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("API-ключ не настроен").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_receipt_api_key_button").assertIsDisplayed().performClick()

        // Dialog should be open
        composeTestRule.onNodeWithTag("receipt_api_key_input").assertIsDisplayed()
            .performTextInput("secret_token_123456")
        composeTestRule.onNodeWithTag("save_receipt_api_key_button").performClick()

        assertEquals("secret_token_123456", savedKey)
    }

    @Test
    fun testReceiptSettings_configured_displaysActiveBadgeAndMaskedKey_andDeletesKey() {
        val state = FinanceUiState(
            isReceiptApiKeyConfigured = true,
            receiptApiKey = "secret_token_123456",
            receiptApiKeyMasked = "secr••••••••3456"
        )

        var clearKeyCalled = false

        composeTestRule.setContent {
            AccountsSettingsScreen(
                viewModel = viewModel,
                state = state,
                onAddAccountClick = {},
                onEditAccount = {},
                onArchiveAccount = {},
                onDeleteAccount = {},
                onCurrencyChange = {},
                onOpenBankSync = {},
                onOpenGeminiAssistant = {},
                onSaveGeminiApiKey = {},
                onTestGeminiApiKey = { _, _ -> },
                onClearGeminiApiKey = {},
                onSaveReceiptApiKey = {},
                onClearReceiptApiKey = { clearKeyCalled = true }
            )
        }

        composeTestRule.onNodeWithTag("accounts_settings_screen")
            .performScrollToNode(hasTestTag("receipt_settings_card"))
        composeTestRule.onNodeWithTag("receipt_settings_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("API «Проверка чека» подключен").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ключ: secr••••••••3456").assertIsDisplayed()

        // Click delete key
        composeTestRule.onNodeWithTag("delete_receipt_api_key_button").performClick()

        // Confirmation dialog appears
        composeTestRule.onNodeWithTag("confirm_delete_receipt_key_button").assertIsDisplayed().performClick()

        assertTrue(clearKeyCalled)
    }
}
