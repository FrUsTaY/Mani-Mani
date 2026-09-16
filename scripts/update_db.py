with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val note: String = ""\n)',
    'val note: String = "",\n    val reminderType: String = "NONE"\n)'
)

with open('app/src/main/java/com/example/data/entity/FinanceEntities.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/data/database/AppDatabase.kt', 'r') as f:
    content = f.read()

content = content.replace('version = 6,', 'version = 7,')

migration = """
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_transactions ADD COLUMN reminderType TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        @Volatile"""

content = content.replace('@Volatile', migration.strip())

content = content.replace(
    '.addMigrations(MIGRATION_4_5)',
    '.addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)'
)

with open('app/src/main/java/com/example/data/database/AppDatabase.kt', 'w') as f:
    f.write(content)

