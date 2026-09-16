import re

# 1. Update FinanceEntities.kt
with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'r') as f:
    entities_content = f.read()

if "PlannedTransactionEntity" not in entities_content:
    entities_content += """
@Entity(tableName = "planned_transactions")
data class PlannedTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // EXPENSE, INCOME, TRANSFER
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val plannedDate: Long,
    val note: String = ""
)
"""
    with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'w') as f:
        f.write(entities_content)


# 2. Update FinanceDaos.kt
with open('app/src/main/java/com/example/data/dao/FinanceDaos.kt', 'r') as f:
    daos_content = f.read()

if "PlannedTransactionDao" not in daos_content:
    daos_content += """
@Dao
interface PlannedTransactionDao {
    @Query("SELECT * FROM planned_transactions ORDER BY plannedDate ASC")
    fun getAllPlannedTransactions(): Flow<List<PlannedTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedTransaction(transaction: PlannedTransactionEntity): Long

    @Update
    suspend fun updatePlannedTransaction(transaction: PlannedTransactionEntity)

    @Delete
    suspend fun deletePlannedTransaction(transaction: PlannedTransactionEntity)

    @Query("DELETE FROM planned_transactions")
    suspend fun deleteAllPlannedTransactions()
}
"""
    with open('app/src/main/java/com/example/data/dao/FinanceDaos.kt', 'w') as f:
        f.write(daos_content)


# 3. Update AppDatabase.kt
with open('app/src/main/java/com/example/data/database/AppDatabase.kt', 'r') as f:
    db_content = f.read()

# Update version
db_content = re.sub(r'version = 5', 'version = 6', db_content)

# Add entity
entities_list_old = r"PendingNotificationEntity::class\s*\]"
entities_list_new = "PendingNotificationEntity::class,\n        PlannedTransactionEntity::class\n    ]"
db_content = re.sub(entities_list_old, entities_list_new, db_content)

# Add abstract fun
dao_fun_old = r"abstract fun pendingNotificationDao\(\): PendingNotificationDao"
dao_fun_new = "abstract fun pendingNotificationDao(): PendingNotificationDao\n    abstract fun plannedTransactionDao(): PlannedTransactionDao"
if "plannedTransactionDao" not in db_content:
    db_content = re.sub(dao_fun_old, dao_fun_new, db_content)

# Add migration 5_6
mig_4_5_old = r"        val MIGRATION_4_5 = object : androidx\.room\.migration\.Migration\(4, 5\) \{\s*override fun migrate\(db: SupportSQLiteDatabase\) \{\s*db\.execSQL\(\"ALTER TABLE accounts ADD COLUMN includeInAnalytics INTEGER NOT NULL DEFAULT 1\"\)\s*\}\s*\}"
mig_5_6 = """        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN includeInAnalytics INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `planned_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, `categoryId` INTEGER, `plannedDate` INTEGER NOT NULL, `note` TEXT NOT NULL)")
            }
        }"""
if "MIGRATION_5_6" not in db_content:
    db_content = re.sub(mig_4_5_old, mig_5_6, db_content)

# add migrations
add_mig_old = r"\.addMigrations\(MIGRATION_4_5\)"
add_mig_new = ".addMigrations(MIGRATION_4_5, MIGRATION_5_6)"
if "MIGRATION_5_6" not in add_mig_old and "MIGRATION_5_6" in add_mig_new:
    db_content = db_content.replace(add_mig_old, add_mig_new)

with open('app/src/main/java/com/example/data/database/AppDatabase.kt', 'w') as f:
    f.write(db_content)

print("Done")
