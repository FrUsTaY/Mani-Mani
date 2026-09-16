with open('app/src/main/java/com/example/data/dao/PendingNotificationDao.kt', 'r') as f:
    content = f.read()

new_method = """    @Query("SELECT * FROM pending_notifications WHERE rawText = :rawText AND timestamp > :sinceTime LIMIT 1")
    suspend fun findRecentDuplicateByText(rawText: String, sinceTime: Long): PendingNotificationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)"""

content = content.replace("    @Insert(onConflict = OnConflictStrategy.REPLACE)", new_method)

with open('app/src/main/java/com/example/data/dao/PendingNotificationDao.kt', 'w') as f:
    f.write(content)
