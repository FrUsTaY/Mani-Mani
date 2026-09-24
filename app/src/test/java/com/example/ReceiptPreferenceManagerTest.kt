package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.receipt.ReceiptPreferenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReceiptPreferenceManagerTest {

    private lateinit var context: Context
    private lateinit var prefsManager: ReceiptPreferenceManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefsManager = ReceiptPreferenceManager(context)
        prefsManager.clearApiKey()
    }

    @Test
    fun saveAndGetApiKey_worksProperly() {
        assertFalse(prefsManager.isApiKeyConfigured())
        assertEquals("", prefsManager.getApiKey())

        prefsManager.saveApiKey("  test_proverka_key_12345  ")
        assertTrue(prefsManager.isApiKeyConfigured())
        assertEquals("test_proverka_key_12345", prefsManager.getApiKey())
    }

    @Test
    fun getMaskedApiKey_masksProperly() {
        assertEquals("", prefsManager.getMaskedApiKey())

        prefsManager.saveApiKey("1234")
        assertEquals("••••••••", prefsManager.getMaskedApiKey())

        prefsManager.saveApiKey("token_secret_1234567890")
        val masked = prefsManager.getMaskedApiKey()
        assertTrue(masked.startsWith("toke"))
        assertTrue(masked.endsWith("7890"))
        assertTrue(masked.contains("••••••••"))
    }

    @Test
    fun clearApiKey_resetsState() {
        prefsManager.saveApiKey("my_key")
        assertTrue(prefsManager.isApiKeyConfigured())

        prefsManager.clearApiKey()
        assertFalse(prefsManager.isApiKeyConfigured())
        assertEquals("", prefsManager.getApiKey())
    }
}
