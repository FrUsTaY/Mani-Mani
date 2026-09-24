package com.example.service.receipt

import android.content.Context
import android.content.SharedPreferences

class ReceiptPreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "receipt_proverka_cheka_prefs"
        private const val KEY_PROVERKA_CHEKA_API_KEY = "proverka_cheka_api_key"
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_PROVERKA_CHEKA_API_KEY, apiKey.trim()).apply()
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_PROVERKA_CHEKA_API_KEY, "") ?: ""
    }

    fun isApiKeyConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_PROVERKA_CHEKA_API_KEY).apply()
    }

    fun getMaskedApiKey(): String {
        val key = getApiKey()
        if (key.isBlank()) return ""
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }
}
