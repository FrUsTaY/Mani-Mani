package com.example.service.gemini

enum class AiPromptType(
    val title: String,
    val iconEmoji: String,
    val description: String,
    val systemPromptAction: String
) {
    FULL_AUDIT(
        title = "Полный аудит",
        iconEmoji = "📊",
        description = "Комплексный анализ доходов, расходов и баланса",
        systemPromptAction = "Проведи глубокий аудит текущих финансов: оцени соотношение доходов и расходов, финансовую устойчивость, главные статьи трат и дай 3 ключевые рекомендации."
    ),
    SAVINGS(
        title = "Где сэкономить?",
        iconEmoji = "💡",
        description = "Поиск перерасходов и скрытых резервов",
        systemPromptAction = "Найди статьи расходов, где пользователь тратит больше обычного, и предложи 4 конкретных реалистичных способа сократить траты без потери качества жизни."
    ),
    INCOME_VS_EXPENSE(
        title = "Доходы vs Расходы",
        iconEmoji = "⚖️",
        description = "Соотношение доходов и расходов за период",
        systemPromptAction = "Проанализируй долю расходов относительно доходов, сформируй оценку профицита или дефицита бюджета и дай рекомендации по балансировке."
    ),
    BUDGETS_AND_GOALS(
        title = "Цели и бюджеты",
        iconEmoji = "🎯",
        description = "Оценка достижимости финансовых целей",
        systemPromptAction = "Проанализируй текущий прогресс по финансовым целям и лимиты бюджетов. Оцени, насколько реалистичны сроки достижения целей с текущим остатком средств в месяц, и как оптимизировать бюджеты."
    ),
    DEBT_AND_SAFETY(
        title = "Подушка и риски",
        iconEmoji = "🛡️",
        description = "Расчёт резервного фонда и долговая нагрузка",
        systemPromptAction = "Оцени баланс долгов (кто должен и кому должен) и рассчитай рекомендуемый размер финансовой подушки безопасности (на 3 и 6 месяцев) исходя из ежемесячных трат."
    ),
    CUSTOM(
        title = "Свой вопрос",
        iconEmoji = "💬",
        description = "Задайте любой вопрос по вашим финансам",
        systemPromptAction = "Ответь на вопрос пользователя, опираясь на его финансовые данные."
    )
}

data class AiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val promptType: AiPromptType? = null
)

enum class MessageSender {
    USER,
    ASSISTANT
}

sealed interface AiState {
    object Idle : AiState
    object Loading : AiState
    data class Success(val responseText: String) : AiState
    data class Error(val errorMessage: String, val isApiKeyMissing: Boolean = false) : AiState
}
