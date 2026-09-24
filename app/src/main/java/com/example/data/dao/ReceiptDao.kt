package com.example.data.dao

import androidx.room.*
import com.example.data.entity.ReceiptEntity
import com.example.data.entity.ReceiptItemEntity
import com.example.data.entity.ReceiptWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Transaction
    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId")
    fun getReceiptByTransactionId(transactionId: Long): Flow<ReceiptWithItems?>

    @Transaction
    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId")
    suspend fun getReceiptByTransactionIdSync(transactionId: Long): ReceiptWithItems?

    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId")
    suspend fun getReceiptEntityByTransactionId(transactionId: Long): ReceiptEntity?

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptById(receiptId: Long): ReceiptWithItems?

    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptEntityById(receiptId: Long): ReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE transactionId = :transactionId")
    suspend fun deleteReceiptByTransactionId(transactionId: Long)

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    suspend fun deleteReceiptById(receiptId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItem(item: ReceiptItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun getItemsByReceiptId(receiptId: Long): List<ReceiptItemEntity>

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteReceiptItemsByReceiptId(receiptId: Long)

    @Query("SELECT * FROM receipts")
    suspend fun getAllReceiptsSync(): List<ReceiptEntity>

    @Query("SELECT * FROM receipt_items")
    suspend fun getAllReceiptItemsSync(): List<ReceiptItemEntity>

    @Query("DELETE FROM receipts")
    suspend fun deleteAllReceipts()

    @Query("DELETE FROM receipt_items")
    suspend fun deleteAllReceiptItems()

    @Transaction
    suspend fun saveReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Long {
        val existing = getReceiptEntityByTransactionId(receipt.transactionId)
        val receiptId = if (existing != null) {
            val toUpdate = receipt.copy(id = existing.id)
            updateReceipt(toUpdate)
            deleteReceiptItemsByReceiptId(existing.id)
            existing.id
        } else {
            insertReceipt(receipt)
        }
        if (items.isNotEmpty()) {
            val itemsWithReceiptId = items.map { it.copy(receiptId = receiptId) }
            insertReceiptItems(itemsWithReceiptId)
        }
        return receiptId
    }
}
