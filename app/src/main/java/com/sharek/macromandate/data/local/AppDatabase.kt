package com.sharek.macromandate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MealEntity::class, AuditEntity::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macro_mandate_db"
                )
                // PRE-RELEASE SAFEGUARD (versionCode 1, no users in the wild yet):
                // schema versions 1-5 predate exportSchema and cannot be reconstructed,
                // so destructive fallback is the only upgrade path today. Before the
                // first public release this must be replaced with real Migration objects
                // (schemas are now exported to app/schemas for exactly that).
                .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
