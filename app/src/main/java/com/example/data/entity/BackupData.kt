package com.example.data.entity

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupData(
    val timestamp: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<GoalEntity>,
    val debts: List<DebtEntity>,
    val plannedTransactions: List<PlannedTransactionEntity>,
    val preferences: BackupPreferences
)

@JsonClass(generateAdapter = true)
data class BackupPreferences(
    val paydayDay: Int,
    val bankOfTheMonth: String,
    val pushNotificationsEnabled: Boolean,
    val themeMode: String,
    val eveningSummaryEnabled: Boolean,
    val eveningSummaryTime: String,
    val bankPushInterceptEnabled: Boolean,
    val zenmoneyPushInterceptEnabled: Boolean
)
