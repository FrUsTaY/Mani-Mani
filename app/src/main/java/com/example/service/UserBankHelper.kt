package com.example.service

import com.example.data.entity.AccountEntity
import com.example.data.entity.CategoryEntity

data class TransferRoutePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceKeyword: String,
    val targetKeyword: String,
    val iconEmoji: String,
    val defaultNote: String
)

object UserBankHelper {

    // Standard preset accounts matching user's exact banking ecosystem
    val DEFAULT_USER_ACCOUNTS = listOf(
        AccountEntity(
            name = "ВТБ • Зарплатный (ЗП)",
            type = "DEBIT",
            balance = 52400.0,
            currency = "RUB",
            colorHex = "#0A2972", // VTB Deep Blue
            iconName = "account_balance",
            orderIndex = 1
        ),
        AccountEntity(
            name = "ВТБ • Продукты",
            type = "DEBIT",
            balance = 18600.0,
            currency = "RUB",
            colorHex = "#1D4ED8", // VTB Bright Blue
            iconName = "shopping_cart",
            orderIndex = 2
        ),
        AccountEntity(
            name = "Т-Банк • Входящие (ЗП жены)",
            type = "DEBIT",
            balance = 70000.0,
            currency = "RUB",
            colorHex = "#EAB308", // Tinkoff / T-Bank Yellow
            iconName = "payments",
            orderIndex = 3
        ),
        AccountEntity(
            name = "Озон Банк • Накопления",
            type = "DEPOSIT",
            balance = 195000.0,
            currency = "RUB",
            colorHex = "#0284C7", // Ozon Blue
            iconName = "savings",
            orderIndex = 4
        ),
        AccountEntity(
            name = "Альфа-Банк • Апельсиновая",
            type = "DEBIT",
            balance = 8200.0,
            currency = "RUB",
            colorHex = "#EF4444", // Alfa Red / Orange Pyaterochka
            iconName = "storefront",
            orderIndex = 5
        ),
        AccountEntity(
            name = "Яндекс Банк • Кэшбэк",
            type = "DEBIT",
            balance = 15000.0,
            currency = "RUB",
            colorHex = "#F59E0B", // Yandex Bank
            iconName = "loyalty",
            orderIndex = 6
        )
    )

    // Quick Me2Me transfer routes between the user's specific banks
    val POPULAR_TRANSFER_ROUTES = listOf(
        TransferRoutePreset(
            id = "vtb_to_vtb_food",
            title = "ВТБ ЗП ➔ ВТБ Продукты",
            subtitle = "Пополнение продуктового счёта внутри ВТБ",
            sourceKeyword = "Зарплатный",
            targetKeyword = "Продукты",
            iconEmoji = "🛒",
            defaultNote = "Пополнение на продукты из остатков ЗП"
        ),
        TransferRoutePreset(
            id = "tinkoff_to_vtb_food",
            title = "Т-Банк ➔ ВТБ Продукты",
            subtitle = "Перевод из ЗП жены на продукты",
            sourceKeyword = "Т-Банк",
            targetKeyword = "Продукты",
            iconEmoji = "🥗",
            defaultNote = "Перевод на продукты (ЗП жены)"
        ),
        TransferRoutePreset(
            id = "tinkoff_to_vtb_salary",
            title = "Т-Банк ➔ ВТБ ЗП",
            subtitle = "Пополнение на общие траты и платежи",
            sourceKeyword = "Т-Банк",
            targetKeyword = "Зарплатный",
            iconEmoji = "💳",
            defaultNote = "Перевод на общие расходы ВТБ"
        ),
        TransferRoutePreset(
            id = "tinkoff_to_ozon",
            title = "Т-Банк ➔ Озон Накопления",
            subtitle = "Отправка части ЗП жены в копилку",
            sourceKeyword = "Т-Банк",
            targetKeyword = "Озон",
            iconEmoji = "📈",
            defaultNote = "В копилку на Озон"
        ),
        TransferRoutePreset(
            id = "tinkoff_to_alfa",
            title = "Т-Банк ➔ Альфа (Пятёрочка)",
            subtitle = "Пополнение Апельсиновой карты",
            sourceKeyword = "Т-Банк",
            targetKeyword = "Альфа",
            iconEmoji = "🍊",
            defaultNote = "Пополнение карты Пятёрочка"
        ),
        TransferRoutePreset(
            id = "tinkoff_to_yandex",
            title = "Т-Банк ➔ Яндекс Банк",
            subtitle = "Пополнение для кэшбэка месяца",
            sourceKeyword = "Т-Банк",
            targetKeyword = "Яндекс",
            iconEmoji = "🎁",
            defaultNote = "Перевод на Яндекс Банк под кэшбэк"
        ),
        TransferRoutePreset(
            id = "vtb_to_ozon",
            title = "ВТБ ЗП ➔ Озон Накопления",
            subtitle = "Сбережение остатка зарплаты на Озон",
            sourceKeyword = "Зарплатный",
            targetKeyword = "Озон",
            iconEmoji = "💰",
            defaultNote = "Перевод остатков ЗП в накопления"
        )
    )

    fun findSourceAccount(accounts: List<AccountEntity>, keyword: String): AccountEntity? {
        return accounts.find { !it.isArchived && it.name.contains(keyword, ignoreCase = true) }
    }

    fun findTargetAccount(accounts: List<AccountEntity>, keyword: String): AccountEntity? {
        return accounts.find { !it.isArchived && it.name.contains(keyword, ignoreCase = true) }
    }

    /**
     * Smartly recommends the appropriate account based on user's banking rules:
     * 1. If merchant/note contains "пятерочка" / "пятёрочка" -> Альфа Апельсиновая.
     * 2. If category is groceries ("Продукты"):
     *    - If Bank of the Month is YANDEX -> Яндекс Банк
     *    - Else -> ВТБ Продукты
     * 3. If other expense:
     *    - If Bank of the Month is YANDEX -> Яндекс Банк
     *    - Else -> ВТБ Зарплатный (ЗП)
     * 4. If income:
     *    - If note contains "жена" / "налич" -> Т-Банк
     *    - Else -> ВТБ Зарплатный
     */
    fun suggestAccount(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        type: String,
        categoryId: Long?,
        note: String,
        bankOfTheMonth: String
    ): AccountEntity? {
        val active = accounts.filter { !it.isArchived }
        val noteLower = note.lowercase()

        // 1. Pyaterochka check
        if (noteLower.contains("пятёрочк") || noteLower.contains("пятерочк") || noteLower.contains("апельсин")) {
            val alfa = active.find { it.name.contains("Альфа", ignoreCase = true) || it.name.contains("Апельсинов", ignoreCase = true) }
            if (alfa != null) return alfa
        }

        val category = categories.find { it.id == categoryId }
        val isGroceries = category?.name?.contains("Продукт", ignoreCase = true) == true ||
                category?.name?.contains("Супермаркет", ignoreCase = true) == true

        if (type == "EXPENSE") {
            if (isGroceries) {
                if (bankOfTheMonth == UserFinancePreferences.BANK_YANDEX) {
                    val yandex = active.find { it.name.contains("Яндекс", ignoreCase = true) }
                    if (yandex != null) return yandex
                }
                val vtbGroceries = active.find { it.name.contains("Продукт", ignoreCase = true) }
                if (vtbGroceries != null) return vtbGroceries
            } else {
                if (bankOfTheMonth == UserFinancePreferences.BANK_YANDEX) {
                    val yandex = active.find { it.name.contains("Яндекс", ignoreCase = true) }
                    if (yandex != null) return yandex
                }
                val vtbSalary = active.find { it.name.contains("Зарплат", ignoreCase = true) || it.name.contains("ЗП", ignoreCase = true) }
                if (vtbSalary != null) return vtbSalary
            }
        } else if (type == "INCOME") {
            if (noteLower.contains("жен") || noteLower.contains("налич") || noteLower.contains("банкомат")) {
                val tbank = active.find { it.name.contains("Т-Банк", ignoreCase = true) || it.name.contains("Тинькофф", ignoreCase = true) }
                if (tbank != null) return tbank
            }
            val vtbSalary = active.find { it.name.contains("Зарплат", ignoreCase = true) || it.name.contains("ЗП", ignoreCase = true) }
            if (vtbSalary != null) return vtbSalary
        }

        return active.firstOrNull()
    }
}
