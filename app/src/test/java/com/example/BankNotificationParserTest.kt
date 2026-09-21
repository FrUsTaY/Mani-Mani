package com.example

import com.example.service.BankNotificationParser
import org.junit.Assert.*
import org.junit.Test

class BankNotificationParserTest {

    @Test
    fun testParseExpenseWithCardAndMerchant() {
        val text = "Покупка 1 250 ₽ в ВкусВилл. Карта *1234. Доступно 14 500 ₽"
        val result = BankNotificationParser.parse(text, title = "Т-Банк", packageName = "com.idamob.tinkoff.android")

        assertNotNull(result)
        assertEquals("Т-Банк", result?.bankName)
        assertEquals("EXPENSE", result?.type)
        assertEquals(1250.0, result?.amount ?: 0.0, 0.01)
        assertEquals("RUB", result?.currency)
        assertEquals("1234", result?.cardLast4)
        assertEquals("продукты", result?.matchedCategoryKeyword)
    }

    @Test
    fun testParseIncomeSalary() {
        val text = "Зачисление зарплаты 95 000 ₽ на карту *5678"
        val result = BankNotificationParser.parse(text, title = "Сбербанк", packageName = "ru.sberbankmobile")

        assertNotNull(result)
        assertEquals("Сбербанк", result?.bankName)
        assertEquals("INCOME", result?.type)
        assertEquals(95000.0, result?.amount ?: 0.0, 0.01)
        assertEquals("RUB", result?.currency)
        assertEquals("5678", result?.cardLast4)
        assertEquals("зарплата", result?.matchedCategoryKeyword)
    }

    @Test
    fun testParseYandexGoTransport() {
        val text = "Списание 450 RUB, Яндекс Go, карта *9999"
        val result = BankNotificationParser.parse(text, title = "Альфа-Банк", packageName = "ru.alfabank.mobile.android")

        assertNotNull(result)
        assertEquals("Альфа-Банк", result?.bankName)
        assertEquals("EXPENSE", result?.type)
        assertEquals(450.0, result?.amount ?: 0.0, 0.01)
        assertEquals("транспорт", result?.matchedCategoryKeyword)
    }

    @Test
    fun testParseZenmoneyWithDecimalCommaAndMerchant() {
        val title = "98,98 ₽, Продукты, Красное&Белое"
        val text = "Продукты с 10 сентября *** ₽. В плане ещё *.\nПолная статистика доступна по подписке.\nВТБ, Доступно: 14 359,83 ₽"
        val result = BankNotificationParser.parse(text, title = title, packageName = "ru.zenmoney.androidsub")

        assertNotNull(result)
        assertEquals("ВТБ", result?.bankName)
        assertEquals("EXPENSE", result?.type)
        assertEquals(98.98, result?.amount ?: 0.0, 0.001)
        assertEquals("RUB", result?.currency)
        assertEquals("Красное&Белое", result?.merchant)
        assertEquals("Продукты", result?.matchedCategoryKeyword)
        assertNull(result?.cardLast4)
    }

    @Test
    fun testParseZenmoneyTransferWithAccount() {
        val title = "10 ₽, Семейные переводы, Алексей Андреевич С."
        val text = "Семейные переводы с 10 сентября *** ₽ (*% от общего расхода).\nИюль, Доступно: 15 379,69 ₽"
        val result = BankNotificationParser.parse(text, title = title, packageName = "ru.zenmoney.androidsub")

        assertNotNull(result)
        assertEquals("Июль", result?.bankName)
        assertEquals("TRANSFER", result?.type)
        assertEquals(10.0, result?.amount ?: 0.0, 0.001)
        assertEquals("Алексей Андреевич С.", result?.merchant)
        assertEquals("Семейные переводы", result?.matchedCategoryKeyword)
    }

    @Test
    fun testParseZenmoneyWithoutCounterparty() {
        val title = "500 ₽, Кафе"
        val text = "ВТБ, Доступно: 5 000 ₽"
        val result = BankNotificationParser.parse(text, title = title, packageName = "ru.zenmoney.android")

        assertNotNull(result)
        assertEquals("ВТБ", result?.bankName)
        assertEquals("EXPENSE", result?.type)
        assertEquals(500.0, result?.amount ?: 0.0, 0.001)
        assertEquals("Кафе", result?.merchant)
        assertEquals("Кафе", result?.matchedCategoryKeyword)
    }

    @Test
    fun testMatchCategoryIdCaseInsensitive() {
        val categories = listOf(
            com.example.data.entity.CategoryEntity(id = 1, name = "Продукты", type = "EXPENSE", iconName = "cart", colorHex = "#00FF00"),
            com.example.data.entity.CategoryEntity(id = 2, name = "Кафе и рестораны", type = "EXPENSE", iconName = "coffee", colorHex = "#00FF00"),
            com.example.data.entity.CategoryEntity(id = 3, name = "Переводы", type = "EXPENSE", iconName = "swap", colorHex = "#00FF00")
        )

        assertEquals(1L, BankNotificationParser.matchCategoryId(categories, "Продукты"))
        assertEquals(1L, BankNotificationParser.matchCategoryId(categories, "продукты"))
        assertEquals(2L, BankNotificationParser.matchCategoryId(categories, "Кафе"))
        assertEquals(3L, BankNotificationParser.matchCategoryId(categories, "Семейные переводы"))
    }

    @Test
    fun testParseZenmoneyBatchNotificationWithSummaryHeader() {
        val title = "Новых операций: 3"
        val lines = listOf(
            "269,97 ₽, Продукты, Пятёрочка",
            "808,91 ₽, Кафе, Додо Пицца",
            "1 500 ₽, Связь, МТС",
            "ВТБ, Доступно: 14 359,83 ₽"
        )
        val results = BankNotificationParser.parseNotification(
            title = title,
            lines = lines,
            packageName = "ru.zenmoney.androidsub"
        )

        assertEquals(3, results.size)

        val op1 = results[0]
        assertEquals("ВТБ", op1.bankName)
        assertEquals(269.97, op1.amount, 0.001)
        assertEquals("Пятёрочка", op1.merchant)
        assertEquals("Продукты", op1.matchedCategoryKeyword)
        assertEquals("EXPENSE", op1.type)

        val op2 = results[1]
        assertEquals(808.91, op2.amount, 0.001)
        assertEquals("Додо Пицца", op2.merchant)
        assertEquals("Кафе", op2.matchedCategoryKeyword)

        val op3 = results[2]
        assertEquals(1500.0, op3.amount, 0.001)
        assertEquals("МТС", op3.merchant)
        assertEquals("Связь", op3.matchedCategoryKeyword)

        // Ensure none of the operations is a phantom 3 ₽ expense
        assertTrue(results.none { it.amount == 3.0 })
    }

    @Test
    fun testSpamFilterBankCreditOffer() {
        val text = "Выгодное предложение, вам доступно 500000 на основные расходы. Оформите кредит прямо сейчас"
        val results = BankNotificationParser.parseNotification(
            title = "Т-Банк",
            text = text,
            packageName = "com.idamob.tinkoff.android"
        )
        assertTrue(results.isEmpty())
        assertNull(BankNotificationParser.parse(text, title = "Т-Банк", packageName = "com.idamob.tinkoff.android"))
    }

    @Test
    fun testCustomUserSpamStopWords() {
        val text = "Ваш лимит по карте 100 000 ₽. Подробнее в приложении"
        val stopWords = setOf("лимит по карте")

        val results = BankNotificationParser.parseNotification(
            title = "Сбербанк",
            text = text,
            packageName = "ru.sberbankmobile",
            userStopWords = stopWords
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun testCleanNoteTrimsServiceGarbage() {
        val merchant = "Самбери"
        val rawText = "Самбери\nВ плане ещё 5 000 ₽. Полная статистика доступна по подписке.\nВТБ, Доступно: 14 359,83 ₽"
        val cleaned = BankNotificationParser.cleanNote(merchant, rawText)
        assertEquals("Самбери", cleaned)
    }

    @Test
    fun testParseBankFourDigitAmountWithoutSpaces() {
        // Test case from real VTB push: 6800р must not be truncated to 680
        val text = "Оплата 6800р Карта*2305 APTECHNOE. Доступно: 12 053,77 ₽"
        val result = BankNotificationParser.parse(
            text = text,
            title = "ВТБ Онлайн",
            packageName = "ru.vtb24.mobilebanking.android"
        )
        assertNotNull(result)
        assertEquals(6800.0, result?.amount ?: 0.0, 0.001)
        assertEquals("RUB", result?.currency)
        assertEquals("2305", result?.cardLast4)
        assertEquals("EXPENSE", result?.type)

        // Other continuous 4-digit patterns
        val res1500 = BankNotificationParser.parse("Покупка 1500р Магнит", packageName = "ru.sberbankmobile")
        assertEquals(1500.0, res1500?.amount ?: 0.0, 0.001)

        val res50000 = BankNotificationParser.parse("Списание 50000 руб.", packageName = "com.idamob.tinkoff.android")
        assertEquals(50000.0, res50000?.amount ?: 0.0, 0.001)
    }
}
