import re

with open('app/src/main/java/com/example/data/database/AppDatabase.kt', 'r') as f:
    content = f.read()

# Modify DatabaseCallback to handle onDestructiveMigration
old_cb = r"""        private class DatabaseCallback\(
            private val scope: CoroutineScope
        \) : RoomDatabase\.Callback\(\) \{
            override fun onCreate\(db: SupportSQLiteDatabase\) \{
                super\.onCreate\(db\)
                INSTANCE\?\.let \{ database ->
                    scope\.launch\(Dispatchers\.IO\) \{
                        prepopulateDatabase\(database\)
                    \}
                \}
            \}
        \}"""

new_cb = """        private class DatabaseCallback(
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
        }"""
content = re.sub(old_cb, new_cb, content)

with open('app/src/main/java/com/example/data/database/AppDatabase.kt', 'w') as f:
    f.write(content)

print("Done")
