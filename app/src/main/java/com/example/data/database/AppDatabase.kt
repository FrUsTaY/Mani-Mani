package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        DebtEntity::class,
        PendingNotificationEntity::class,
        PlannedTransactionEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun debtDao(): DebtDao
    abstract fun pendingNotificationDao(): PendingNotificationDao
    abstract fun plannedTransactionDao(): PlannedTransactionDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN includeInAnalytics INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `planned_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, `categoryId` INTEGER, `plannedDate` INTEGER NOT NULL, `note` TEXT NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_transactions ADD COLUMN reminderType TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manimani_database"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        prepopulateDatabase(database)
                    }
                }
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        prepopulateDatabase(database)
                    }
                }
            }
        }

        suspend fun prepopulateDatabase(database: AppDatabase) {
            val categoryDao = database.categoryDao()

            // Clear old categories when prepopulating
            categoryDao.deleteAllCategories()

            // Predefined expense categories
            val expenseCategories = listOf(
                CategoryEntity(name = "Продукты и супермаркеты", type = "EXPENSE", iconName = "shopping_cart", colorHex = "#10B981", orderIndex = 1),
                CategoryEntity(name = "Кафе и рестораны", type = "EXPENSE", iconName = "restaurant", colorHex = "#F59E0B", orderIndex = 2),
                CategoryEntity(name = "Транспорт и авто", type = "EXPENSE", iconName = "directions_car", colorHex = "#3B82F6", orderIndex = 3),
                CategoryEntity(name = "Жильё и ЖКХ", type = "EXPENSE", iconName = "home", colorHex = "#6366F1", orderIndex = 4),
                CategoryEntity(name = "Покупки и одежда", type = "EXPENSE", iconName = "checkroom", colorHex = "#EC4899", orderIndex = 5),
                CategoryEntity(name = "Здоровье и аптеки", type = "EXPENSE", iconName = "medical_services", colorHex = "#EF4444", orderIndex = 6),
                CategoryEntity(name = "Развлечения и отдых", type = "EXPENSE", iconName = "movie", colorHex = "#8B5CF6", orderIndex = 7),
                CategoryEntity(name = "Подписки и сервисы", type = "EXPENSE", iconName = "subscriptions", colorHex = "#06B6D4", orderIndex = 8),
                CategoryEntity(name = "Связь и интернет", type = "EXPENSE", iconName = "wifi", colorHex = "#14B8A6", orderIndex = 9),
                CategoryEntity(name = "Образование", type = "EXPENSE", iconName = "school", colorHex = "#F97316", orderIndex = 10),
                CategoryEntity(name = "Другое (расход)", type = "EXPENSE", iconName = "more_horiz", colorHex = "#64748B", orderIndex = 11)
            )

            // Predefined income categories
            val incomeCategories = listOf(
                CategoryEntity(name = "Зарплата", type = "INCOME", iconName = "payments", colorHex = "#10B981", orderIndex = 1),
                CategoryEntity(name = "Аванс", type = "INCOME", iconName = "payments", colorHex = "#34D399", orderIndex = 2),
                CategoryEntity(name = "Фриланс / Проекты", type = "INCOME", iconName = "laptop", colorHex = "#3B82F6", orderIndex = 3),
                CategoryEntity(name = "Кэшбэк и проценты", type = "INCOME", iconName = "trending_up", colorHex = "#F59E0B", orderIndex = 4),
                CategoryEntity(name = "Подарки", type = "INCOME", iconName = "redeem", colorHex = "#EC4899", orderIndex = 5),
                CategoryEntity(name = "Другое (доход)", type = "INCOME", iconName = "more_horiz", colorHex = "#64748B", orderIndex = 6)
            )

            categoryDao.insertCategories(expenseCategories)
            categoryDao.insertCategories(incomeCategories)
        }

        suspend fun applyUserBankStructure(database: AppDatabase) {
            val categoryDao = database.categoryDao()
            val accountDao = database.accountDao()
            val transactionDao = database.transactionDao()
            val budgetDao = database.budgetDao()
            val goalDao = database.goalDao()
            val debtDao = database.debtDao()

            // Clear old data
            accountDao.deleteAllAccounts()
            transactionDao.deleteAllTransactions()
            budgetDao.deleteAllBudgets()
            goalDao.deleteAllGoals()
            debtDao.deleteAllDebts()

            // Ensure categories exist
            val categories = categoryDao.getAllCategoriesSync()
            var expenseIds = categories.filter { it.type == "EXPENSE" }.sortedBy { it.orderIndex }.map { it.id }
            var incomeIds = categories.filter { it.type == "INCOME" }.sortedBy { it.orderIndex }.map { it.id }

            if (expenseIds.isEmpty() || incomeIds.isEmpty()) {
                prepopulateDatabase(database)
                val newCategories = categoryDao.getAllCategoriesSync()
                expenseIds = newCategories.filter { it.type == "EXPENSE" }.sortedBy { it.orderIndex }.map { it.id }
                incomeIds = newCategories.filter { it.type == "INCOME" }.sortedBy { it.orderIndex }.map { it.id }
            }

            // Predefined accounts matching user's multi-bank workflow:
            // 1. VTB Salary
            val vtbSalaryId = accountDao.insertAccount(
                AccountEntity(
                    name = "ВТБ • Зарплатный (ЗП)",
                    type = "DEBIT",
                    balance = 52400.0,
                    currency = "RUB",
                    colorHex = "#0A2972",
                    iconName = "account_balance",
                    orderIndex = 1
                )
            )

            // 2. VTB Groceries
            val vtbGroceriesId = accountDao.insertAccount(
                AccountEntity(
                    name = "ВТБ • Продукты",
                    type = "DEBIT",
                    balance = 18600.0,
                    currency = "RUB",
                    colorHex = "#1D4ED8",
                    iconName = "shopping_cart",
                    orderIndex = 2
                )
            )

            // 3. T-Bank (Tinkoff) Incoming & Cash Wife Salary
            val tbankId = accountDao.insertAccount(
                AccountEntity(
                    name = "Т-Банк • Входящие (ЗП жены)",
                    type = "DEBIT",
                    balance = 70000.0,
                    currency = "RUB",
                    colorHex = "#EAB308",
                    iconName = "payments",
                    orderIndex = 3
                )
            )

            // 4. Ozon Bank Savings
            val ozonId = accountDao.insertAccount(
                AccountEntity(
                    name = "Озон Банк • Накопления",
                    type = "DEPOSIT",
                    balance = 195000.0,
                    currency = "RUB",
                    colorHex = "#0284C7",
                    iconName = "savings",
                    orderIndex = 4
                )
            )

            // 5. Alfa Bank Orange Pyaterochka
            val alfaId = accountDao.insertAccount(
                AccountEntity(
                    name = "Альфа-Банк • Апельсиновая",
                    type = "DEBIT",
                    balance = 8200.0,
                    currency = "RUB",
                    colorHex = "#EF4444",
                    iconName = "storefront",
                    orderIndex = 5
                )
            )

            // 6. Yandex Bank Cashback
            val yandexId = accountDao.insertAccount(
                AccountEntity(
                    name = "Яндекс Банк • Кэшбэк",
                    type = "DEBIT",
                    balance = 15000.0,
                    currency = "RUB",
                    colorHex = "#F59E0B",
                    iconName = "loyalty",
                    orderIndex = 6
                )
            )

            // Initial transactions reflecting user's flow
            val now = System.currentTimeMillis()
            val oneDay = 86400000L

            // Husband's salary on VTB
            if (incomeIds.isNotEmpty()) {
                transactionDao.insertTransaction(
                    TransactionEntity(
                        type = "INCOME",
                        amount = 95000.0,
                        accountId = vtbSalaryId,
                        categoryId = incomeIds[0], // Зарплата
                        timestamp = now - 2 * oneDay,
                        note = "Зарплата на ВТБ за предыдущий месяц",
                        tag = "работа,втб"
                    )
                )
                
                if (incomeIds.size > 1) {
                    // Wife's salary deposited in cash via ATM into T-Bank
                    transactionDao.insertTransaction(
                        TransactionEntity(
                            type = "INCOME",
                            amount = 65000.0,
                            accountId = tbankId,
                            categoryId = incomeIds[1], // Зарплата жены
                            timestamp = now - 2 * oneDay + 3600000L,
                            note = "Внесение наличными ЗП жены через банкомат",
                            tag = "жена,наличные,тбанк"
                        )
                    )
                }
            }

            // Me2Me Transfer: from T-Bank to VTB Groceries
            transactionDao.insertTransaction(
                TransactionEntity(
                    type = "TRANSFER",
                    amount = 15000.0,
                    accountId = tbankId,
                    toAccountId = vtbGroceriesId,
                    timestamp = now - oneDay - 4 * 3600000L,
                    note = "Распределение: на продукты в ВТБ",
                    tag = "перевод,продукты"
                )
            )

            // Me2Me Transfer: from T-Bank to Ozon Savings
            transactionDao.insertTransaction(
                TransactionEntity(
                    type = "TRANSFER",
                    amount = 20000.0,
                    accountId = tbankId,
                    toAccountId = ozonId,
                    timestamp = now - oneDay - 3 * 3600000L,
                    note = "Распределение: в копилку на Озон",
                    tag = "перевод,накопления"
                )
            )

            // Me2Me Transfer: from T-Bank to Alfa Pyaterochka
            transactionDao.insertTransaction(
                TransactionEntity(
                    type = "TRANSFER",
                    amount = 5000.0,
                    accountId = tbankId,
                    toAccountId = alfaId,
                    timestamp = now - oneDay - 2 * 3600000L,
                    note = "Распределение: на Апельсиновую карту",
                    tag = "перевод,пятёрочка"
                )
            )

            // Me2Me Transfer: internal VTB salary to VTB groceries
            transactionDao.insertTransaction(
                TransactionEntity(
                    type = "TRANSFER",
                    amount = 10000.0,
                    accountId = vtbSalaryId,
                    toAccountId = vtbGroceriesId,
                    timestamp = now - oneDay,
                    note = "Пополнение продуктового счёта ВТБ",
                    tag = "перевод,втб"
                )
            )

            if (expenseIds.isNotEmpty()) {
                // Expense: Pyaterochka on Alfa card only
                transactionDao.insertTransaction(
                    TransactionEntity(
                        type = "EXPENSE",
                        amount = 2150.0,
                        accountId = alfaId,
                        categoryId = expenseIds[0], // Продукты
                        timestamp = now - 18 * 3600000L,
                        note = "Пятёрочка у дома (Апельсиновая карта)",
                        tag = "пятёрочка,еда"
                    )
                )

                // Expense: VTB Groceries
                transactionDao.insertTransaction(
                    TransactionEntity(
                        type = "EXPENSE",
                        amount = 3450.0,
                        accountId = vtbGroceriesId,
                        categoryId = expenseIds[0], // Продукты
                        timestamp = now - 12 * 3600000L,
                        note = "Супермаркет Перекрёсток",
                        tag = "продукты,дом"
                    )
                )
                
                if (expenseIds.size > 4) {
                    // Expense: VTB Salary (non-grocery purchase)
                    transactionDao.insertTransaction(
                        TransactionEntity(
                            type = "EXPENSE",
                            amount = 4600.0,
                            accountId = vtbSalaryId,
                            categoryId = expenseIds[4], // Покупки и одежда
                            timestamp = now - 5 * 3600000L,
                            note = "Осенняя куртка и обувь",
                            tag = "одежда,покупки"
                        )
                    )
                }

                // Initial monthly budgets
                budgetDao.insertBudget(
                    BudgetEntity(
                        categoryId = expenseIds[0], // Продукты
                        limitAmount = 35000.0,
                        periodMonth = "DEFAULT"
                    )
                )
                if (expenseIds.size > 1) {
                    budgetDao.insertBudget(
                        BudgetEntity(
                            categoryId = expenseIds[1], // Кафе
                            limitAmount = 15000.0,
                            periodMonth = "DEFAULT"
                        )
                    )
                }
                if (expenseIds.size > 2) {
                    budgetDao.insertBudget(
                        BudgetEntity(
                            categoryId = expenseIds[2], // Транспорт
                            limitAmount = 10000.0,
                            periodMonth = "DEFAULT"
                        )
                    )
                }
            }

            // Initial goals
            goalDao.insertGoal(
                GoalEntity(
                    name = "Поездка в отпуск",
                    targetAmount = 150000.0,
                    currentAmount = 85000.0,
                    iconName = "flight",
                    colorHex = "#3B82F6"
                )
            )
            goalDao.insertGoal(
                GoalEntity(
                    name = "Финансовая подушка",
                    targetAmount = 300000.0,
                    currentAmount = 250000.0,
                    iconName = "shield",
                    colorHex = "#10B981"
                )
            )

            // Initial debts
            debtDao.insertDebt(
                DebtEntity(
                    personName = "Алексей (коллега)",
                    amount = 3500.0,
                    isOwedToMe = true,
                    note = "За совместный обед и такси",
                    isSettled = false
                )
            )
            debtDao.insertDebt(
                DebtEntity(
                    personName = "Оплата аренды оборудования",
                    amount = 5000.0,
                    isOwedToMe = false,
                    note = "До конца месяца",
                    isSettled = false
                )
            )
        }
        }
    }
