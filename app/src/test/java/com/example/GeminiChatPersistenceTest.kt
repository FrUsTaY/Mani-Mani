package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.FinanceRepository
import com.example.service.gemini.AiMessage
import com.example.service.gemini.AiPromptType
import com.example.service.gemini.MessageSender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiChatPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAiMessagesAreSavedAndRetrievedInOrder() = runBlocking {
        // Initial state should be empty
        val initialMessages = repository.allAiMessages.first()
        assertTrue(initialMessages.isEmpty())

        // 1. User sends message
        val userMsg = AiMessage(
            id = "msg-1",
            sender = MessageSender.USER,
            text = "Проведи полный аудит финансов",
            timestamp = 1000L,
            promptType = AiPromptType.FULL_AUDIT
        )
        repository.saveAiMessage(userMsg)

        // 2. Assistant replies
        val assistantMsg = AiMessage(
            id = "msg-2",
            sender = MessageSender.ASSISTANT,
            text = "Ваш баланс стабилен, но есть перерасход по кафе.",
            timestamp = 2000L,
            promptType = AiPromptType.FULL_AUDIT
        )
        repository.saveAiMessage(assistantMsg)

        // 3. Follow-up user question
        val followUpMsg = AiMessage(
            id = "msg-3",
            sender = MessageSender.USER,
            text = "Как именно сократить траты?",
            timestamp = 3000L,
            promptType = AiPromptType.CUSTOM
        )
        repository.saveAiMessage(followUpMsg)

        // Verify retrieval
        val messages = repository.allAiMessages.first()
        assertEquals(3, messages.size)

        assertEquals("msg-1", messages[0].id)
        assertEquals(MessageSender.USER, messages[0].sender)
        assertEquals("Проведи полный аудит финансов", messages[0].text)
        assertEquals(AiPromptType.FULL_AUDIT, messages[0].promptType)

        assertEquals("msg-2", messages[1].id)
        assertEquals(MessageSender.ASSISTANT, messages[1].sender)
        assertEquals("Ваш баланс стабилен, но есть перерасход по кафе.", messages[1].text)

        assertEquals("msg-3", messages[2].id)
        assertEquals(MessageSender.USER, messages[2].sender)
        assertEquals("Как именно сократить траты?", messages[2].text)
        assertEquals(AiPromptType.CUSTOM, messages[2].promptType)
    }

    @Test
    fun testClearAiChatDeletesAllSavedMessages() = runBlocking {
        repository.saveAiMessage(
            AiMessage(
                id = "msg-1",
                sender = MessageSender.USER,
                text = "Привет",
                timestamp = 1000L
            )
        )
        repository.saveAiMessage(
            AiMessage(
                id = "msg-2",
                sender = MessageSender.ASSISTANT,
                text = "Привет! Чем помочь?",
                timestamp = 2000L
            )
        )

        assertEquals(2, repository.allAiMessages.first().size)

        repository.clearAiMessages()

        val afterClear = repository.allAiMessages.first()
        assertTrue(afterClear.isEmpty())
    }
}
