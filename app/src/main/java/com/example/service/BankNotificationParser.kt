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
        "com.ftc.robank" to "Росбанк",
        "com.yandex.bank" to "Яндекс Банк"
    )

    fun identifyBank(packageName: String, title: String?, text: String?): String {
        KNOWN_BANK_PACKAGES[packageName]?.let { return it }
        val full = "${title ?: ""} ${text ?: ""}".lowercase()
        return when {
            full.contains("яндекс") || full.contains("yandex") -> "Яндекс Банк"
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
    private val SYSTEM_STOP_PATTERNS = listOf(
        Regex("""оформите\s+кредит""", RegexOption.IGNORE_CASE),
        Regex("""вам\s+доступно""", RegexOption.IGNORE_CASE),
        Regex("""вам\s+одобрен""", RegexOption.IGNORE_CASE),
        Regex("""предварительно\s+одобрен""", RegexOption.IGNORE_CASE),
        Regex("""подайте\s+заявку""", RegexOption.IGNORE_CASE),
        Regex("""кэшбэк\s+до\s+\d+""", RegexOption.IGNORE_CASE),
        Regex("""ставка\s+от\s+\d+""", RegexOption.IGNORE_CASE),
        Regex("""откройте\s+вклад""", RegexOption.IGNORE_CASE),
        Regex("""код\s+подтверждения""", RegexOption.IGNORE_CASE),
        Regex("""никому\s+не\s+сообщайте""", RegexOption.IGNORE_CASE),
        Regex("""пароль\s+для\s+входа""", RegexOption.IGNORE_CASE),
        Regex("""специальное\s+предложение""", RegexOption.IGNORE_CASE),
        Regex("""выгодное\s+предложение""", RegexOption.IGNORE_CASE),
        Regex("""лимит\s+по\s+карте\s+увеличен""", RegexOption.IGNORE_CASE)
    )

    fun isSpamOrMarketing(text: String, userStopWords: Set<String> = emptySet()): Boolean {
        val lower = text.lowercase()
        // Check user custom stop words first
        for (stopWord in userStopWords) {
            val trimmed = stopWord.trim().lowercase()
            if (trimmed.isNotBlank() && lower.contains(trimmed)) {
                return true
            }
        }
        // Check system marketing patterns
        for (pattern in SYSTEM_STOP_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                // Ignore if it's not a real receipt (i.e. if it lacks definite transaction verbs)
                val hasDefiniteTransactionVerb = lower.contains("покупка") || lower.contains("списание") ||
                        lower.contains("оплачено") || lower.contains("оплата товаров") || lower.contains("чек")
                if (!hasDefiniteTransactionVerb) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Cleans merchant name or extracts a user-friendly note from the raw push text.
     */
    fun cleanNote(merchant: String, rawText: String = ""): String {
        var clean = merchant.trim()
        if (clean.isBlank() || clean == "Покупка" || clean == "Пополнение счёта" || clean == "Операция Дзен-мани") {
            // Try extracting from raw text
            val firstLine = rawText.lines().firstOrNull()?.trim() ?: ""
            if (firstLine.isNotBlank() && !firstLine.startsWith("Новых операций")) {
                clean = firstLine
            }
        }
        // Remove junk phrases from services (e.g. Zenmoney promo footer)
        clean = clean.replace(Regex("""(?i)\s*в плане ещё.*"""), "")
            .replace(Regex("""(?i)\s*полная статистика.*"""), "")
            .replace(Regex("""(?i)\s*доступно:.*"""), "")
            .trim()
            .removeSuffix(",")

        if (clean.endsWith(".") && !clean.matches(Regex(""".*(?:^|\s)[a-zA-Zа-яА-ЯёЁ]\.$"""))) {
            clean = clean.removeSuffix(".")
        }

        return clean.trim().ifBlank { merchant }
    }

    /**
     * Parses standard bank push notifications or SMS texts.
     * Retained for backward compatibility, returning the first parsed operation.
     */
    fun parse(text: String, title: String? = null, packageName: String = "", userStopWords: Set<String> = emptySet()): ParsedNotificationResult? {
        return parseNotification(title = title, text = text, lines = null, packageName = packageName, userStopWords = userStopWords).firstOrNull()
    }

    /**
     * Parses notifications returning all discovered transactions (e.g. multi-line batch from Zen-money).
     */
    fun parseNotification(
        title: String? = null,
        text: String? = null,
        lines: List<CharSequence>? = null,
        packageName: String = "",
        userStopWords: Set<String> = emptySet()
    ): List<ParsedNotificationResult> {
        val combinedFullText = "${title ?: ""} ${text ?: ""} ${lines?.joinToString(" ") ?: ""}".trim()
        if (combinedFullText.isBlank()) return emptyList()

        // Check spam filter
        if (isSpamOrMarketing(combinedFullText, userStopWords)) {
            return emptyList()
        }

        val isZenmoney = packageName == "ru.zenmoney.androidsub" || packageName == "ru.zenmoney.android"

        if (isZenmoney) {
            return parseZenmoneyNotification(title, text, lines)
        }

        // Standard single bank push parsing
        val single = parseStandardBankPush(title, text ?: "", packageName)
        return if (single != null) listOf(single) else emptyList()
    }

    private fun parseZenmoneyNotification(
        title: String?,
        text: String?,
        lines: List<CharSequence>?
    ): List<ParsedNotificationResult> {
        val results = mutableListOf<ParsedNotificationResult>()

        // 1. Collect all candidate lines
        val candidateLines = mutableListOf<String>()
        lines?.forEach { line ->
            val str = line.toString().trim()
            if (str.isNotBlank()) candidateLines.add(str)
        }
        text?.lines()?.forEach { line ->
            val str = line.trim()
            if (str.isNotBlank() && !candidateLines.contains(str)) {
                candidateLines.add(str)
            }
        }

        val zenPattern = Regex("""^([0-9\s\u00A0]+(?:[.,][0-9]{1,2})?\s*(?:₽|руб\.?|rub|р\.|\$|usd|€|eur|₸|kzt|byn|cny)?)\s*,\s*([^,]+?)(?:,\s*(.+))?$""", RegexOption.IGNORE_CASE)

        // Try to extract bank / account name from text or lines
        // Examples:
        // "ВТБ, Доступно: 14 359,83 ₽"
        // "Июль, Доступно: 15 379,69 ₽"
        var bankName = "Дзен-мани"
        val fullContent = "${text ?: ""}\n${candidateLines.joinToString("\n")}"
        val bankRegex = Regex("""(?:^|\n)([^\n,]+?)\s*,\s*(?:доступно|баланс|остаток):""", RegexOption.IGNORE_CASE)
        val bankMatch = bankRegex.find(fullContent)
        if (bankMatch != null) {
            val candidate = bankMatch.groups[1]?.value?.trim()?.trimEnd('.', ',')
            if (!candidate.isNullOrBlank() && candidate.length <= 25) {
                bankName = candidate
            }
        }

        fun tryParseLine(line: String): ParsedNotificationResult? {
            val trimmed = line.trim()
            // Skip summary headers like "Новых операций: 3"
            if (trimmed.startsWith("Новых операций", ignoreCase = true) ||
                trimmed.startsWith("Операций:", ignoreCase = true) ||
                trimmed.contains("доступно:", ignoreCase = true) ||
                trimmed.contains("баланс:", ignoreCase = true)) {
                return null
            }

            val match = zenPattern.find(trimmed)
            val (amountRaw, categoryName, merchantRaw) = if (match != null) {
                Triple(match.groups[1]?.value?.trim() ?: "", match.groups[2]?.value?.trim() ?: "", match.groups[3]?.value?.trim())
            } else {
                val parts = trimmed.split(Regex(""",\s+""")).map { it.trim() }
                if (parts.size >= 2) {
                    Triple(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, parts.getOrNull(2))
                } else {
                    return null
                }
            }

            val amountStr = amountRaw.replace("[^0-9.,]".toRegex(), "").replace(" ", "").replace("\u00A0", "").replace(",", ".")
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0.0) return null

            val merchant = if (!merchantRaw.isNullOrBlank()) {
                cleanNote(merchantRaw)
            } else if (categoryName.isNotBlank()) {
                cleanNote(categoryName)
            } else {
                "Операция Дзен-мани"
            }

            // Determine currency
            val currencyStr = amountRaw.lowercase()
            val currency = when {
                currencyStr.contains("$") || currencyStr.contains("usd") -> "USD"
                currencyStr.contains("€") || currencyStr.contains("eur") -> "EUR"
                currencyStr.contains("₸") || currencyStr.contains("kzt") -> "KZT"
                currencyStr.contains("byn") -> "BYN"
                currencyStr.contains("cny") -> "CNY"
                else -> "RUB"
            }

            // Transaction type
            val catLower = categoryName.lowercase()
            var type = "EXPENSE"
            if (catLower.contains("доход") || catLower.contains("зарплат") || catLower.contains("пополнен") ||
                catLower.contains("аванс") || catLower.contains("возврат") || catLower.contains("кэшбэк") ||
                catLower.contains("начислен")) {
                type = "INCOME"
            } else if (catLower.contains("перевод") || trimmed.lowercase().contains("перевод")) {
                type = "TRANSFER"
            }

            return ParsedNotificationResult(
                bankName = bankName,
                type = type,
                amount = amount,
                currency = currency,
                merchant = merchant,
                cardLast4 = null,
                matchedCategoryKeyword = categoryName.ifBlank { null }
            )
        }

        // Case A: Multiple lines extracted from lines/text
        for (line in candidateLines) {
            val res = tryParseLine(line)
            if (res != null) {
                results.add(res)
            }
        }

        // Case B: Single operation where title itself holds the transaction
        if (results.isEmpty() && title != null) {
            val titleRes = tryParseLine(title)
            if (titleRes != null) {
                results.add(titleRes)
            }
        }

        return results
    }

    private fun parseStandardBankPush(title: String?, text: String, packageName: String): ParsedNotificationResult? {
        val raw = "${title ?: ""} $text".trim()
        if (raw.isBlank()) return null

        val bankName = identifyBank(packageName, title, text)
        val lower = raw.lowercase()

        // 1. Determine Type
        val type = when {
            lower.contains("зачислен") || lower.contains("пополнен") || lower.contains("перевод от") ||
                    lower.contains("зарплат") || lower.contains("аванс") || lower.contains("возврат") ||
                    lower.contains("поступил") -> "INCOME"

            lower.contains("перевод") || lower.contains("перевели") || lower.contains("сбп") -> "TRANSFER"

            lower.contains("покупка") || lower.contains("списание") || lower.contains("оплата") ||
                    lower.contains("чек") || lower.contains("снятие") || lower.contains("платёж") ||
                    lower.contains("платеж") || lower.contains("отправлен") -> "EXPENSE"

            else -> "EXPENSE"
        }

        // 2. Extract Amount
        // Support numbers with space thousand separator (requiring at least one group: 10 000) OR continuous digits (6800)
        val amountRegex = Regex("""(?<!\w)(?:([0-9]{1,3}(?:[\s\u00A0][0-9]{3})+(?:[.,][0-9]{1,2})?)|([0-9]+(?:[.,][0-9]{1,2})?))\s*(₽|руб\.?|rub|р\.?|\$|usd|€|eur|₸|kzt|byn|cny)?""", RegexOption.IGNORE_CASE)
        val matches = amountRegex.findAll(raw).toList()
        if (matches.isEmpty()) return null

        data class CandidateMatch(
            val amount: Double,
            val currency: String,
            val hasExplicitCurrency: Boolean,
            val startIndex: Int
        )

        val candidateMatches = mutableListOf<CandidateMatch>()

        for (match in matches) {
            val numStr = (match.groups[1]?.value ?: match.groups[2]?.value)
                ?.replace(" ", "")
                ?.replace("\u00A0", "")
                ?.replace(",", ".")
            val currStr = match.groups[3]?.value?.lowercase()

            val parsedNum = numStr?.toDoubleOrNull()
            if (parsedNum != null && parsedNum > 0) {
                val beforeMatch = raw.substring(0, match.range.first).lowercase().trim()
                if (beforeMatch.endsWith("карта *") || beforeMatch.endsWith("карте *") ||
                    beforeMatch.endsWith("счет *") || beforeMatch.endsWith("счёт *") ||
                    beforeMatch.endsWith("карта*") || beforeMatch.endsWith("карте*") ||
                    beforeMatch.endsWith("карта") || beforeMatch.endsWith("карте") ||
                    beforeMatch.endsWith("доступно:") || beforeMatch.endsWith("доступно") ||
                    beforeMatch.endsWith("баланс:") || beforeMatch.endsWith("баланс") ||
                    beforeMatch.endsWith("остаток:") || beforeMatch.endsWith("остаток")) {
                    continue
                }
                val detectedCurrency = when {
                    currStr?.contains("$") == true || currStr?.contains("usd") == true -> "USD"
                    currStr?.contains("€") == true || currStr?.contains("eur") == true -> "EUR"
                    currStr?.contains("₸") == true || currStr?.contains("kzt") == true -> "KZT"
                    currStr?.contains("byn") == true -> "BYN"
                    currStr?.contains("cny") == true -> "CNY"
                    else -> "RUB"
                }
                candidateMatches.add(
                    CandidateMatch(
                        amount = parsedNum,
                        currency = detectedCurrency,
                        hasExplicitCurrency = !currStr.isNullOrBlank(),
                        startIndex = match.range.first
                    )
                )
            }
        }

        if (candidateMatches.isEmpty()) return null

        // Prefer the first candidate with an explicit currency symbol (e.g. 6800р), otherwise take the first valid number
        val chosen = candidateMatches.firstOrNull { it.hasExplicitCurrency } ?: candidateMatches.first()
        val matchedAmount = chosen.amount
        val detectedCurrency = chosen.currency

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
            merchant = cleanNote(merchant, raw),
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
        if (keyword.isNullOrBlank()) return null
        val kw = keyword.trim().lowercase()
        return categories.find { cat ->
            val catName = cat.name.trim().lowercase()
            catName == kw || catName.contains(kw) || kw.contains(catName) ||
                    (catName.length >= 4 && kw.startsWith(catName.take(4))) ||
                    (kw.length >= 4 && catName.startsWith(kw.take(4)))
        }?.id
    }
}
