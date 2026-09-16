package com.example.data.dao

import androidx.room.*
import com.example.data.entity.PendingNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingNotificationDao {
    @Query("SELECT * FROM pending_notifications WHERE isProcessed = 0 ORDER BY timestamp DESC")
    fun getUnprocessedNotifications(): Flow<List<PendingNotificationEntity>>

    @Query("SELECT * FROM pending_notifications ORDER BY timestamp DESC LIMIT 50")
    fun getAllRecentNotifications(): Flow<List<PendingNotificationEntity>>

    @Query("SELECT * FROM pending_notifications WHERE rawText = :rawText AND timestamp > :sinceTime LIMIT 1")
    suspend fun findRecentDuplicateByText(rawText: String, sinceTime: Long): PendingNotificationEntity?

    @Query("SELECT * FROM pending_notifications WHERE packageName = :packageName AND amount = :amount AND type = :type AND timestamp > :sinceTime LIMIT 1")
    suspend fun findRecentDuplicateByDetails(packageName: String, amount: Double, type: String, sinceTime: Long): PendingNotificationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: PendingNotificationEntity): Long

    @Query("UPDATE pending_notifications SET isProcessed = 1 WHERE id = :id")
    suspend fun markAsProcessed(id: Long)

    @Delete
    suspend fun deleteNotification(notification: PendingNotificationEntity)

    @Query("DELETE FROM pending_notifications WHERE isProcessed = 1")
    suspend fun clearProcessed()

    @Query("DELETE FROM pending_notifications")
    suspend fun deleteAllNotifications()
}
