package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "accounts")
@JsonClass(generateAdapter = true)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // CASH, DEBIT, CREDIT, DEPOSIT, INVESTMENT, CRYPTO
    val balance: Double,
    val currency: String = "RUB",
    val colorHex: String = "#10B981",
    val iconName: String = "account_balance_wallet",
    val isArchived: Boolean = false,
    val includeInTotal: Boolean = true,
    val includeInAnalytics: Boolean = true,
    val orderIndex: Int = 0
)

@Entity(tableName = "categories")
@JsonClass(generateAdapter = true)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // EXPENSE, INCOME
    val iconName: String,
    val colorHex: String,
    val orderIndex: Int = 0
)

@Entity(tableName = "transactions")
@JsonClass(generateAdapter = true)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // EXPENSE, INCOME, TRANSFER
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val tag: String = "",
    val excludeFromStats: Boolean = false,
    val goalId: Long? = null,
    val debtId: Long? = null
)

@Entity(tableName = "budgets")
@JsonClass(generateAdapter = true)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null, // null for total budget
    val limitAmount: Double,
    val periodMonth: String // e.g. "2026-09" or "DEFAULT"
)

@Entity(tableName = "goals")
@JsonClass(generateAdapter = true)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetDate: Long? = null,
    val iconName: String = "flag",
    val colorHex: String = "#10B981"
)

@Entity(tableName = "debts")
@JsonClass(generateAdapter = true)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val amount: Double,
    val isOwedToMe: Boolean, // true = Мне должны, false = Я должен
    val dueDate: Long? = null,
    val note: String = "",
    val isSettled: Boolean = false
)

@Entity(tableName = "planned_transactions")
@JsonClass(generateAdapter = true)
data class PlannedTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // EXPENSE, INCOME, TRANSFER
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val plannedDate: Long,
    val note: String = "",
    val reminderType: String = "NONE"
)
