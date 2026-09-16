package com.example.service.gemini

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class GeminiPreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gemini_ai_prefs"
        private const val KEY_USER_API_KEY = "user_gemini_api_key"
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_USER_API_KEY, apiKey.trim()).apply()
    }

    fun getUserApiKey(): String {
        return prefs.getString(KEY_USER_API_KEY, "") ?: ""
    }

    fun getEffectiveApiKey(): String {
        val userKey = getUserApiKey()
        if (userKey.isNotBlank()) {
            return userKey
        }
        // Fallback to BuildConfig if present and not default placeholder
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        return if (buildKey.isNotBlank() && !buildKey.equals("MY_GEMINI_API_KEY", ignoreCase = true)) {
            buildKey
        } else {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_USER_API_KEY).apply()
    }

    fun getMaskedApiKey(): String {
        val key = getEffectiveApiKey()
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }
}
