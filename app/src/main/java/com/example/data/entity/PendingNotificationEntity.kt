package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_notifications")
data class PendingNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val bankName: String,
    val rawText: String,
    val type: String, // EXPENSE, INCOME, TRANSFER, UNKNOWN
    val amount: Double,
    val currency: String = "RUB",
    val merchantOrSender: String = "",
    val cardLast4: String? = null,
    val suggestedCategoryId: Long? = null,
    val suggestedAccountId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false
)
