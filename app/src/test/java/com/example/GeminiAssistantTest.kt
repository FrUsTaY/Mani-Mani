package com.example

import com.example.service.gemini.AiPromptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeminiAssistantTest {

    @Test
    fun testAiPromptTypesHaveRussianLabels() {
        assertEquals("Полный аудит", AiPromptType.FULL_AUDIT.title)
        assertEquals("Где сэкономить?", AiPromptType.SAVINGS.title)
        assertEquals("Цели и бюджеты", AiPromptType.BUDGETS_AND_GOALS.title)
        assertEquals("Подушка и риски", AiPromptType.DEBT_AND_SAFETY.title)
        assertEquals("Свой вопрос", AiPromptType.CUSTOM.title)
    }

    @Test
    fun testPromptTypeEmojis() {
        for (type in AiPromptType.entries) {
            assertNotNull(type.iconEmoji)
            assert(type.iconEmoji.isNotBlank())
        }
    }
}
