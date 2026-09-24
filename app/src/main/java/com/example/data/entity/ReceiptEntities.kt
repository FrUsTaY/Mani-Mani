package com.example.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"], unique = true)
    ]
)
@JsonClass(generateAdapter = true)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val imagePath: String? = null,
    val merchant: String? = null,
    val dateTime: Long? = null,
    val total: Double? = null,
    val fiscalNumber: String? = null,
    val fiscalDocument: String? = null,
    val fiscalSign: String? = null,
    val operationType: String? = null,
    val rawQrData: String? = null
)

@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["receiptId"])
    ]
)
@JsonClass(generateAdapter = true)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val name: String,
    val quantity: Double = 1.0,
    val price: Double = 0.0,
    val total: Double = 0.0
)

data class ReceiptWithItems(
    @Embedded val receipt: ReceiptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiptId"
    )
    val items: List<ReceiptItemEntity> = emptyList()
)
