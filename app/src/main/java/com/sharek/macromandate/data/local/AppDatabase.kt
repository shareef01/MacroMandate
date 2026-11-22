package com.sharek.macromandate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MealEntity::class, AuditEntity::class], version = 8, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Version 1 is the original schema in Git. The next checked-in schema is version 6.
        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN assessment TEXT")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN isRestricted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN isNightRefueling INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, category TEXT NOT NULL, message TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entries_timestamp` ON `meal_entries` (`timestamp`)")
            }
        }

        // Public v7 already has this index. Earlier local v7 builds lack it.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_timestamp` ON `audit_log` (`timestamp`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Re-check inside the lock. Two threads can both pass the outer null
                // check, and building twice would leave two Room handles open on one
                // SQLite file — the surveillance service, the widget's provideGlance,
                // and the enforcement worker all call this independently.
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macro_mandate_db"
                )
                    .addMigrations(MIGRATION_1_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
