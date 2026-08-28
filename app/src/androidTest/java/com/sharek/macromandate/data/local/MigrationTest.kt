package com.sharek.macromandate.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that upgrading the database keeps the user's meals.
 *
 * `AppDatabase` shipped with `fallbackToDestructiveMigration()`, which silently
 * drops and recreates every table when a migration is missing. On a build in
 * anyone's hands that is total, unannounced loss of their nutrition history.
 * That fallback is now debug-only, so a release build that meets a database it
 * cannot migrate fails loudly instead — and this test is what keeps the
 * migration path honest as the schema moves.
 *
 * Requires a connected device or emulator (`./gradlew connectedDebugAndroidTest`).
 * It was not executed during the audit that introduced it: no device was
 * attached to the machine, so it is written but **unverified**.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate6To7_preservesMealRows() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO meal_entries
                    (id, timestamp, imageUri, foodName, calories, proteinGrams, carbsGrams,
                     fatGrams, isLiquid, latitude, longitude, assessment, isRestricted, isNightRefueling)
                VALUES
                    ('meal-1', 1700000000000, NULL, 'Porridge', 350, 12.5, 60.0, 6.0, 0,
                     51.5074, -0.1278, 'Recorded.', 0, 0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        db.query("SELECT id, foodName, calories, proteinGrams, latitude FROM meal_entries").use { cursor ->
            assertEquals("the meal row must survive the upgrade", 1, cursor.count)
            cursor.moveToFirst()
            assertEquals("meal-1", cursor.getString(0))
            assertEquals("Porridge", cursor.getString(1))
            assertEquals(350, cursor.getInt(2))
            assertEquals(12.5f, cursor.getFloat(3), 0.001f)
            assertEquals(51.5074, cursor.getDouble(4), 0.0001)
        }
    }

    @Test
    fun migrate6To7_preservesAuditRows() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO audit_log (timestamp, category, message) " +
                    "VALUES (1700000000000, 'DATA_INGEST', 'RECORD LOGGED')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        db.query("SELECT category, message FROM audit_log").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("DATA_INGEST", cursor.getString(0))
            assertEquals("RECORD LOGGED", cursor.getString(1))
        }
    }

    @Test
    fun migrate6To7_createsTheTimestampIndices() {
        helper.createDatabase(TEST_DB, 6).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        val indices = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { cursor ->
            while (cursor.moveToNext()) indices.add(cursor.getString(0))
        }

        // Every meal query filters or orders by timestamp; without these each one
        // was a full table scan.
        assertTrue("meal index missing", indices.contains("index_meal_entries_timestamp"))
        assertTrue("audit index missing", indices.contains("index_audit_log_timestamp"))
    }

    @Test
    fun migrate6To7_isNonDestructiveWithManyRows() {
        helper.createDatabase(TEST_DB, 6).apply {
            repeat(500) { i ->
                execSQL(
                    """
                    INSERT INTO meal_entries
                        (id, timestamp, imageUri, foodName, calories, proteinGrams, carbsGrams,
                         fatGrams, isLiquid, latitude, longitude, assessment, isRestricted, isNightRefueling)
                    VALUES ('meal-$i', ${1700000000000L + i}, NULL, 'Meal $i', $i, 1.0, 2.0, 3.0, 0,
                            NULL, NULL, NULL, 0, 0)
                    """.trimIndent()
                )
            }
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        db.query("SELECT COUNT(*) FROM meal_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals(500, cursor.getInt(0))
        }
    }
}
