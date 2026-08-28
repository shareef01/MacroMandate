package com.sharek.macromandate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sharek.macromandate.BuildConfig

@Database(entities = [MealEntity::class, AuditEntity::class], version = 7, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Adds the index every meal query already relied on.
         *
         * `meal_entries` had no index at all, while every read filters or orders
         * by `timestamp` — today's totals, the weekly window, the history list.
         * SQLite answered each one with a full table scan plus a sort.
         *
         * Data-preserving by construction: `CREATE INDEX` rewrites no rows.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meal_entries_timestamp " +
                        "ON meal_entries (timestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_audit_log_timestamp " +
                        "ON audit_log (timestamp)"
                )
            }
        }

        private val MIGRATIONS = arrayOf(MIGRATION_6_7)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Re-check inside the lock. Two threads can both pass the outer null
                // check, and building twice would leave two Room handles open on one
                // SQLite file — the widget's provideGlance, the enforcement worker,
                // and the ViewModel all call this independently.
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macro_mandate_db"
                )
                    .addMigrations(*MIGRATIONS)
                    // Destructive fallback silently deletes every meal the user
                    // has logged when it fires. It used to be unconditional, with
                    // a comment noting that schemas 1-5 predate exportSchema and
                    // could not be reconstructed — which is true, and is exactly
                    // why it must not survive into a shipped build. A release
                    // build now fails to open a database it cannot migrate,
                    // rather than emptying it.
                    //
                    // In debug it stays, so that schema iteration during
                    // development does not require a migration for every change.
                    .apply {
                        if (BuildConfig.DEBUG) {
                            @Suppress("DEPRECATION")
                            fallbackToDestructiveMigration()
                        }
                    }
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
