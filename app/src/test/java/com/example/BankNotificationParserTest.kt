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
}
