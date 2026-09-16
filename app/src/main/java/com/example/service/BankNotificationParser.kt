package com.example.service

import com.example.data.entity.CategoryEntity

data class ParsedNotificationResult(
    val bankName: String,
    val type: String, // EXPENSE, INCOME, TRANSFER, UNKNOWN
    val amount: Double,
    val currency: String,
    val merchant: String,
    val cardLast4: String?,
    val matchedCategoryKeyword: String? = null
)

object BankNotificationParser {

    // Package names to Bank labels
    val KNOWN_BANK_PACKAGES = mapOf(
        "com.idamob.tinkoff.android" to "Т-Банк",
        "ru.tinkoff.mb.business" to "Т-Банк",
        "ru.sberbankmobile" to "Сбербанк",
        "ru.sberbank.sberinvestor" to "Сбербанк",
        "ru.alfabank.mobile.android" to "Альфа-Банк",
        "ru.vtb24.mobilebanking.android" to "ВТБ",
        "ru.raiffeisennews" to "Райффайзен",
        "ru.gazprombank.android.mobilebank.app" to "Газпромбанк",
        "ru.ozon.fintech" to "Ozon Банк",
        "com.bspb" to "Банк Санкт-Петербург",
        "kz.kaspi.mobile" to "Kaspi.kz",
        "ru.sovcomcard.halva.v1" to "Совкомбанк (Халва)",
        "com.ftc.robank" to "Росбанк"
    )

    fun identifyBank(packageName: String, title: String?, text: String?): String {
        KNOWN_BANK_PACKAGES[packageName]?.let { return it }
        val full = "${title ?: ""} ${text ?: ""}".lowercase()
        return when {
            full.contains("тинькофф") || full.contains("т-банк") || full.contains("t-bank") || full.contains("tinkoff") -> "Т-Банк"
            full.contains("сбер") || full.contains("sber") -> "Сбербанк"
            full.contains("альфа") || full.contains("alfa") -> "Альфа-Банк"
            full.contains("втб") || full.contains("vtb") -> "ВТБ"
            full.contains("озон") || full.contains("ozon") -> "Ozon Банк"
            full.contains("райффайзен") || full.contains("raiff") -> "Райффайзен"
            full.contains("газпром") || full.contains("gpb") -> "Газпромбанк"
            full.contains("kaspi") || full.contains("каспи") -> "Kaspi.kz"
            else -> "Банковское уведомление"
        }
    }

    /**
     * Parses standard bank push notifications or SMS texts.
     * Examples:
     * - "Покупка 1 250 ₽ в ВкусВилл. Карта *1234. Доступно 14 500 ₽"
     * - "Списание 350.50 RUB, Yandex Go, баланс 5000"
     * - "Перевод 5000 ₽ от Иван И. Сообщение: за обед"
     * - "Зачисление зарплаты 85 000 ₽ на карту *5678"
     * - "Оплата 420 руб. Пятерочка"
     */
    fun parse(text: String, title: String? = null, packageName: String = ""): ParsedNotificationResult? {
        val isZenmoney = packageName == "ru.zenmoney.androidsub" || packageName == "ru.zenmoney.android"
        
        if (isZenmoney && title != null) {
            // Zenmoney format: "209,97 ₽, Продукты, Самбери" or "10 ₽, Семейные переводы, Алексей Андреевич С."
            val parts = title.split(",").map { it.trim() }
            if (parts.size >= 2) {
                // Parse amount from parts[0]
                val amountStr = parts[0].replace("[^0-9.,]".toRegex(), "").replace(",", ".")
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                
                if (amount > 0) {
                    val categoryName = parts.getOrNull(1) ?: ""
                    val merchant = parts.getOrNull(2) ?: categoryName.ifEmpty { "Операция Дзен-мани" }
                    
                    // Determine currency
                    val currencyStr = parts[0].lowercase()
                    val currency = when {
                        currencyStr.contains("$") || currencyStr.contains("usd") -> "USD"
                        currencyStr.contains("€") || currencyStr.contains("eur") -> "EUR"
                        currencyStr.contains("₸") || currencyStr.contains("kzt") -> "KZT"
                        currencyStr.contains("byn") -> "BYN"
                        currencyStr.contains("cny") -> "CNY"
                        else -> "RUB"
                    }
                    
                    // Try to extract bank from text "ВТБ, Доступно:..."
                    var bankName = "Дзен-мани"
                    // Find text before "Доступно:"
                    val textParts = text.split("Доступно:")
                    if (textParts.size == 2) {
                        val bankCandidate = textParts[0].split(".").lastOrNull()?.trim()
                        if (bankCandidate != null && bankCandidate.isNotBlank()) {
                            // "Полная статистика доступна по подписке. ВТБ,"
                            val b = bankCandidate.trimEnd(',')
                            if (b.isNotBlank() && b.length < 15) {
                                bankName = b
                            }
                        }
                    }

                    // Guessing type (Zenmoney does not explicitly say income or expense in title usually, 
                    // but we can assume expense unless category gives it away)
                    var type = "EXPENSE"
                    if (categoryName.lowercase().contains("доход") || categoryName.lowercase().contains("зарплата") || categoryName.lowercase().contains("пополнение")) {
                        type = "INCOME"
                    } else if (categoryName.lowercase().contains("перевод")) {
                        type = "TRANSFER" // Or could be expense
                    }

                    return ParsedNotificationResult(
                        bankName = bankName,
                        type = type,
                        amount = amount,
                        currency = currency,
                        merchant = merchant,
                        cardLast4 = categoryName, // Hack to pass category name via cardLast4 since we don't have a note field in ParsedNotificationResult
                        matchedCategoryKeyword = categoryName 
                    )
                }
            }
        }

        val raw = "${title ?: ""} $text".trim()
        if (raw.isBlank()) return null

        val bankName = identifyBank(packageName, title, text)
        val lower = raw.lowercase()

        // 1. Determine Type
        val type = when {
            lower.contains("зачислен") || lower.contains("пополнен") || lower.contains("перевод от") ||
                    lower.contains("зарплат") || lower.contains("аванс") || lower.contains("возврат") ||
                    lower.contains("поступил") -> "INCOME"

            lower.contains("перевод на") || lower.contains("перевод клиенту") || lower.contains("перевели") -> "TRANSFER"

            lower.contains("покупка") || lower.contains("списание") || lower.contains("оплата") ||
                    lower.contains("чек") || lower.contains("снятие") || lower.contains("платёж") ||
                    lower.contains("платеж") || lower.contains("отправлен") -> "EXPENSE"

            else -> "EXPENSE" // Default assumption for financial alerts with amounts
        }

        // 2. Extract Amount
        // Look for patterns like: 1 250.50 ₽, 1250,50 руб, 5000.00 RUB, $15.99, 15000 KZT
        val amountRegex = Regex("""(?<!\w)(?:([0-9]{1,3}(?:[\s\u00A0][0-9]{3})*(?:[.,][0-9]{1,2})?)|([0-9]+(?:[.,][0-9]{1,2})?))\s*(₽|руб\.?|rub|р\.|\$|usd|€|eur|₸|kzt|byn|cny)?""", RegexOption.IGNORE_CASE)
        val matches = amountRegex.findAll(raw).toList()
        if (matches.isEmpty()) return null

        // Usually the first valid amount in the message is the transaction amount (second is often "Доступно / Баланс")
        var matchedAmount: Double? = null
        var detectedCurrency = "RUB"

        for (match in matches) {
            val numStr = (match.groups[1]?.value ?: match.groups[2]?.value)
                ?.replace(" ", "")
                ?.replace("\u00A0", "")
                ?.replace(",", ".")
            val currStr = match.groups[3]?.value?.lowercase()

            val parsedNum = numStr?.toDoubleOrNull()
            if (parsedNum != null && parsedNum > 0) {
                // Avoid matching card numbers like *1234 or dates like 2026
                val beforeMatch = raw.substring(0, match.range.first).lowercase()
                if (beforeMatch.endsWith("карта *") || beforeMatch.endsWith("карте *") || beforeMatch.endsWith("счет *")) {
                    continue
                }
                matchedAmount = parsedNum
                detectedCurrency = when {
                    currStr?.contains("$") == true || currStr?.contains("usd") == true -> "USD"
                    currStr?.contains("€") == true || currStr?.contains("eur") == true -> "EUR"
                    currStr?.contains("₸") == true || currStr?.contains("kzt") == true -> "KZT"
                    currStr?.contains("byn") == true -> "BYN"
                    currStr?.contains("cny") == true -> "CNY"
                    else -> "RUB"
                }
                break
            }
        }

        if (matchedAmount == null) return null

        // 3. Extract Card Last 4
        val cardRegex = Regex("""(?:\*|карта|карте|счет|счёт)\s*\*?([0-9]{4})\b""", RegexOption.IGNORE_CASE)
        val cardLast4 = cardRegex.find(raw)?.groups?.get(1)?.value

        // 4. Extract Merchant / Recipient
        var merchant = ""
        val merchantPatterns = listOf(
            Regex("""(?:в|в\s+магазине|место:|точка:)\s+([A-Za-zА-Яа-я0-9\s\-_.«»"]{2,30}?)(?:\.|\,|\s+карта|\s+баланс|\s+доступно|\s*$|\s*\()""", RegexOption.IGNORE_CASE),
            Regex("""(?:покупка|оплата|списание)\s+(?:[0-9\s.,₽рубa-z$€₸]+)\s+([A-Za-zА-Яа-я0-9\s\-_.«»"]{2,30}?)(?:\.|\,|\s+баланс|\s+доступно|\s*$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:от|кому:)\s+([A-Za-zА-Яа-я0-9\s\-_.]{2,30}?)(?:\.|\,|\s+баланс|\s+доступно|\s*$)""", RegexOption.IGNORE_CASE)
        )

        for (p in merchantPatterns) {
            val m = p.find(raw)
            if (m != null) {
                val candidate = m.groups[1]?.value?.trim()?.trimEnd('.', ',')
                if (!candidate.isNullOrBlank() && candidate.length > 1 && !candidate.startsWith("доступно") && !candidate.startsWith("баланс")) {
                    merchant = candidate
                    break
                }
            }
        }

        if (merchant.isBlank()) {
            merchant = if (type == "INCOME") "Пополнение счёта" else "Покупка"
        }

        // 5. Predict category based on merchant or text keywords
        val matchedKeyword = findCategoryKeyword(lower)

        return ParsedNotificationResult(
            bankName = bankName,
            type = type,
            amount = matchedAmount,
            currency = detectedCurrency,
            merchant = merchant,
            cardLast4 = cardLast4,
            matchedCategoryKeyword = matchedKeyword
        )
    }

    private fun findCategoryKeyword(lower: String): String? {
        val keywords = mapOf(
            "продукты" to listOf("пятерочка", "перекресток", "магнит", "вкусвилл", "дикси", "лента", "ашан", "супермаркет", "продукты", "spar", "метро"),
            "кафе" to listOf("кафе", "ресторан", "кофе", "coffee", "бургер", "додо", "вкусно и точка", "kfc", "шоколадница", "доставка еды", "яндекс еда", "delivery club"),
            "транспорт" to listOf("яндекс go", "такси", "метро", "парковка", "лукойл", "газпромнефть", "азс", "роснефть", "каршеринг", "бензин", "авто"),
            "покупки" to listOf("wildberries", "вайлдберриз", "ozon", "яндекс маркет", "алиэкспресс", "aliexpress", "шопинг", "одежда", "заказ"),
            "жильё" to listOf("жкх", "мосэнерго", "квартплата", "аренда", "дом"),
            "здоровье" to listOf("аптека", "горздрав", "стоматолог", "клиника", "инвитро", "гемотест", "ригла", "доктор"),
            "подписки" to listOf("яндекс плюс", "apple", "google", "spotify", "кинопоиск", "подписка", "youtube"),
            "связь" to listOf("мтс", "билайн", "мегафон", "tele2", "т-мобайл", "интернет", "связь"),
            "зарплата" to listOf("зарплат", "аванс", "начисление зп", "оплата труда")
        )

        for ((catName, words) in keywords) {
            for (w in words) {
                if (lower.contains(w)) return catName
            }
        }
        return null
    }

    fun matchCategoryId(categories: List<CategoryEntity>, keyword: String?): Long? {
        if (keyword == null) return null
        return categories.find { cat ->
            cat.name.lowercase().contains(keyword) || keyword.contains(cat.name.lowercase().take(5))
        }?.id
    }
}
