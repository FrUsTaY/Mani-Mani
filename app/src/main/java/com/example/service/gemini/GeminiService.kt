package com.example.service.gemini

import android.util.Log
import com.example.data.entity.*
import com.example.ui.viewmodel.BudgetProgress
import com.example.ui.viewmodel.CategorySpending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiService(private val preferenceManager: GeminiPreferenceManager) {

    companion object {
        private const val TAG = "GeminiService"
        // Target model mandated by Gemini API guidelines for general text reasoning
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun testApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val testKey = apiKey.trim()
        if (testKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ключ API не может быть пустым"))
        }

        try {
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray()
                    partsArray.put(JSONObject().apply { put("text", "Ответь одним словом: 'OK'") })
                    put("parts", partsArray)
                }
                contentsArray.put(contentObj)
                put("contents", contentsArray)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$testKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody, response.code)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                Result.success("Ключ действителен! Gemini готов к работе.")
            } else {
                Result.failure(Exception("Получен пустой ответ от сервиса"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test API key failed", e)
            Result.failure(Exception(formatNetworkException(e)))
        }
    }

    suspend fun askAssistant(
        promptType: AiPromptType,
        userQuestion: String? = null,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetProgress>,
        goals: List<GoalEntity>,
        debts: List<DebtEntity>,
        baseCurrency: String,
        monthlyIncome: Double,
        monthlyExpense: Double,
        categorySpendings: List<CategorySpending>,
        conversationHistory: List<AiMessage> = emptyList(),
        payday: Int = 10,
        paydayPeriodLabel: String = "",
        daysUntilPayday: Int = 0,
        bankOfTheMonth: String = "VTB"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = preferenceManager.getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API-ключ Gemini не настроен. Пожалуйста, укажите его в Настройках."))
        }

        try {
            val financialSummary = buildFinancialContext(
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                budgets = budgets,
                goals = goals,
                debts = debts,
                baseCurrency = baseCurrency,
                monthlyIncome = monthlyIncome,
                monthlyExpense = monthlyExpense,
                categorySpendings = categorySpendings,
                payday = payday,
                paydayPeriodLabel = paydayPeriodLabel,
                daysUntilPayday = daysUntilPayday,
                bankOfTheMonth = bankOfTheMonth
            )

            val systemInstructionText = """
                Ты — профессиональный, тактичный и опытный финансовый ИИ-консультант в приложении "Мани-мани".
                Твоя задача — помогать пользователю грамотно распоряжаться деньгами, находить возможности для экономии,
                достигать целей и избегать финансовых ловушек.
                
                Правила:
                1. Отвечай на чистом, живом русском языке.
                2. Опирайся строго на актуальные финансовые данные пользователя и специфику его банков.
                3. Структурируй ответ: используй маркированные списки, жирный шрифт для ключевых цифр и категорий, эмодзи для разделов.
                4. Давай точные рекомендации по балансировке между счетами (Me2Me переводы), контролю продуктового бюджета до следующей зарплаты ($payday-го числа) и распределению входящих средств.
                5. Будь позитивным, поддерживающим и конструктивным.
                
                ДАННЫЕ ПОЛЬЗОВАТЕЛЯ И БАНКОВ:
                $financialSummary
            """.trimIndent()

            val requestJson = JSONObject().apply {
                // System instructions
                put("systemInstruction", JSONObject().apply {
                    val parts = JSONArray()
                    parts.put(JSONObject().apply { put("text", systemInstructionText) })
                    put("parts", parts)
                })

                // Contents / History
                val contentsArray = JSONArray()

                // Recent conversation context
                conversationHistory.takeLast(6).forEach { msg ->
                    val contentObj = JSONObject().apply {
                        put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                        val parts = JSONArray()
                        parts.put(JSONObject().apply { put("text", msg.text) })
                        put("parts", parts)
                    }
                    contentsArray.put(contentObj)
                }

                // Current user turn
                val currentTurnText = if (userQuestion.isNullOrBlank()) {
                    promptType.systemPromptAction
                } else {
                    "${promptType.systemPromptAction}\n\nВопрос пользователя: $userQuestion"
                }

                val currentContentObj = JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray()
                    parts.put(JSONObject().apply { put("text", currentTurnText) })
                    put("parts", parts)
                }
                contentsArray.put(currentContentObj)

                put("contents", contentsArray)

                // Generation config
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody, response.code)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Gemini не сформировал ответ. Попробуйте переформулировать запрос."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error querying Gemini", e)
            Result.failure(Exception(formatNetworkException(e)))
        }
    }

    private fun buildFinancialContext(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetProgress>,
        goals: List<GoalEntity>,
        debts: List<DebtEntity>,
        baseCurrency: String,
        monthlyIncome: Double,
        monthlyExpense: Double,
        categorySpendings: List<CategorySpending>,
        payday: Int = 10,
        paydayPeriodLabel: String = "",
        daysUntilPayday: Int = 0,
        bankOfTheMonth: String = "VTB"
    ): String {
        val sb = StringBuilder()
        val totalBalance = accounts.filter { !it.isArchived }.sumOf { it.balance }
        sb.appendLine("Базовая валюта: $baseCurrency")
        sb.appendLine("Общий баланс активных счетов: %.2f $baseCurrency".format(Locale.US, totalBalance))
        sb.appendLine("Финансовый цикл (по зарплате): $payday-е число месяца (текущий период: $paydayPeriodLabel)")
        sb.appendLine("Дней до следующей зарплаты: $daysUntilPayday")
        sb.appendLine("Основной банк месяца: ${if (bankOfTheMonth == "YANDEX") "Яндекс Банк (активирован выгодный кэшбэк)" else "ВТБ"}")
        sb.appendLine("Доходы за текущий финансовый месяц: %.2f $baseCurrency".format(Locale.US, monthlyIncome))
        sb.appendLine("Расходы за текущий финансовый месяц: %.2f $baseCurrency".format(Locale.US, monthlyExpense))
        val netSavings = monthlyIncome - monthlyExpense
        sb.appendLine("Чистый остаток / Дефицит в этом цикле: %.2f $baseCurrency".format(Locale.US, netSavings))

        sb.appendLine("\nСтруктура счетов и банков пользователя:")
        accounts.filter { !it.isArchived }.forEach { acc ->
            val bankHint = when {
                acc.name.contains("Зарплат", ignoreCase = true) -> " [Зарплата мужа 10-го числа, оплата всех покупок кроме продуктов]"
                acc.name.contains("Продукт", ignoreCase = true) -> " [Счёт строго для покупки продуктов]"
                acc.name.contains("Входящ", ignoreCase = true) || acc.name.contains("Т-Банк", ignoreCase = true) -> " [Шлюз для наличной ЗП жены через банкомат и последующего распределения]"
                acc.name.contains("Озон", ignoreCase = true) -> " [Счёт накоплений и сбережений]"
                acc.name.contains("Альфа", ignoreCase = true) || acc.name.contains("Апельсин", ignoreCase = true) -> " [Апельсиновая карта — ТОЛЬКО для магазина Пятёрочка]"
                acc.name.contains("Яндекс", ignoreCase = true) -> " [Карта под повышенный кэшбэк]"
                else -> ""
            }
            sb.appendLine("- ${acc.name} (${acc.type}): %.2f ${acc.currency}$bankHint".format(Locale.US, acc.balance))
        }

        sb.appendLine("\nРасходы по категориям в этом месяце:")
        if (categorySpendings.isEmpty()) {
            sb.appendLine("- Нет расходов в этом месяце")
        } else {
            categorySpendings.forEach { cs ->
                sb.appendLine("- ${cs.category.name}: %.2f $baseCurrency (%.1f%% от всех трат)".format(Locale.US, cs.totalAmount, cs.percentage))
            }
        }

        sb.appendLine("\nБюджеты:")
        if (budgets.isEmpty()) {
            sb.appendLine("- Лимиты бюджетов не установлены")
        } else {
            budgets.forEach { b ->
                val status = if (b.isOverBudget) "ПРЕВЫШЕН!" else "в норме"
                sb.appendLine("- ${b.category?.name ?: "Категория"}: потрачено %.2f из %.2f $baseCurrency (%.0f%%, $status)".format(
                    Locale.US, b.spent, b.limit, b.percent * 100f
                ))
            }
        }

        sb.appendLine("\nФинансовые цели:")
        if (goals.isEmpty()) {
            sb.appendLine("- Цели не заданы")
        } else {
            goals.forEach { g ->
                val percent = if (g.targetAmount > 0) (g.currentAmount / g.targetAmount) * 100 else 0.0
                sb.appendLine("- ${g.name}: %.2f из %.2f $baseCurrency (прогресс %.1f%%)".format(
                    Locale.US, g.currentAmount, g.targetAmount, percent
                ))
            }
        }

        sb.appendLine("\nДолги:")
        val owedToMe = debts.filter { it.isOwedToMe && !it.isSettled }
        val iOwe = debts.filter { !it.isOwedToMe && !it.isSettled }
        sb.appendLine("- Мне должны (всего %.2f): %s".format(
            Locale.US,
            owedToMe.sumOf { it.amount },
            if (owedToMe.isEmpty()) "нет" else owedToMe.joinToString { "${it.personName}: %.0f".format(Locale.US, it.amount) }
        ))
        sb.appendLine("- Я должен (всего %.2f): %s".format(
            Locale.US,
            iOwe.sumOf { it.amount },
            if (iOwe.isEmpty()) "нет" else iOwe.joinToString { "${it.personName}: %.0f".format(Locale.US, it.amount) }
        ))

        sb.appendLine("\nПоследние операции:")
        val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
        transactions.take(10).forEach { tx ->
            val sign = if (tx.type == "INCOME") "+" else "-"
            sb.appendLine("- [${dateFormat.format(Date(tx.timestamp))}] $sign%.2f: ${tx.note.ifBlank { tx.type }}".format(
                Locale.US, tx.amount
            ))
        }

        return sb.toString()
    }

    private fun parseErrorMessage(responseBody: String, httpCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val error = json.optJSONObject("error")
            val message = error?.optString("message") ?: ""
            val status = error?.optString("status") ?: ""

            when {
                httpCode == 400 || status == "INVALID_ARGUMENT" -> {
                    if (message.contains("API key not valid", ignoreCase = true) || message.contains("API_KEY_INVALID", ignoreCase = true)) {
                        "Неверный API ключ Gemini. Проверьте правильность ключа в настройках."
                    } else {
                        "Ошибка параметров запроса: $message"
                    }
                }
                httpCode == 403 || status == "PERMISSION_DENIED" -> {
                    "Доступ запрещен. Проверьте, включен ли Gemini API для вашего ключа в Google AI Studio."
                }
                httpCode == 429 || status == "RESOURCE_EXHAUSTED" -> {
                    "Превышен лимит запросов к Gemini API. Пожалуйста, подождите минуту и попробуйте снова."
                }
                httpCode >= 500 -> {
                    "Сервер Gemini временно недоступен. Попробуйте чуть позже."
                }
                message.isNotBlank() -> message
                else -> "Ошибка сервера ($httpCode)"
            }
        } catch (e: Exception) {
            "Ошибка соединения ($httpCode): $responseBody"
        }
    }

    private fun formatNetworkException(e: Exception): String {
        return when {
            e is java.net.UnknownHostException -> "Нет подключения к интернету. Проверьте сеть."
            e is java.net.SocketTimeoutException -> "Время ожидания ответа от Gemini истекло (таймаут). Попробуйте снова."
            e.message?.contains("Failed to connect", ignoreCase = true) == true -> "Не удалось связаться с сервером Google Gemini."
            else -> e.localizedMessage ?: "Неизвестная ошибка сети"
        }
    }
}
