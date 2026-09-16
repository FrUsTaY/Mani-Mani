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
}
