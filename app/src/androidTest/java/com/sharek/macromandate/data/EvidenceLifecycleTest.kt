package com.sharek.macromandate.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.DossierExporter
import com.sharek.macromandate.util.EvidenceStore
import com.sharek.macromandate.util.KeyStoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EvidenceLifecycleTest {
    private lateinit var context: Context
    private lateinit var root: File

    @Before
    fun setup() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        root = Files.createTempDirectory(application.cacheDir.toPath(), "evidence-test-").toFile()
        context = object : ContextWrapper(application) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
    }

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun stagingCleanupKeepsAgedActiveCapture() {
        val old = File(EvidenceStore.stagingDirectory(context), "old.jpg")
        old.writeText("old")
        val active = EvidenceStore.newStagingFile(context, UUID.randomUUID().toString())
        active.writeText("capture")
        val yesterday = System.currentTimeMillis() - 25L * 60 * 60 * 1000
        assertTrue(old.setLastModified(yesterday))
        assertTrue(active.setLastModified(yesterday))

        EvidenceStore.reconcileStaging(context)
        assertFalse(old.exists())
        assertTrue(active.exists())
        EvidenceStore.releaseStaging(Uri.fromFile(active))
        EvidenceStore.reconcileStaging(context)
        assertFalse(active.exists())
    }

    @Test
    fun failedCopyPreservesOriginalAndCanBeRetried() {
        val original = EvidenceStore.newStagingFile(context, UUID.randomUUID().toString())
        original.writeText("frame")
        val source = Uri.fromFile(original)
        assertNull(EvidenceStore.commit(context, Uri.fromFile(File(root, "missing"))))
        assertEquals("frame", original.readText())
        val saved = checkNotNull(EvidenceStore.commit(context, source))
        assertEquals("frame", File(checkNotNull(saved.path)).readText())
        assertEquals("frame", original.readText())
        EvidenceStore.releaseStaging(source)
    }

    @Test
    fun deletionRejectsSiblingDirectoryAndTraversal() {
        val evidence = EvidenceStore.directory(context)
        val outside = File(evidence.parentFile, "evidence-other/photo.jpg")
        outside.parentFile?.mkdirs()
        outside.writeText("outside")
        assertTrue(EvidenceStore.delete(context, Uri.fromFile(outside).toString()).isSuccess)
        val traversal = File(evidence, "../evidence-other/photo.jpg")
        assertTrue(EvidenceStore.delete(context, Uri.fromFile(traversal).toString()).isSuccess)
        assertEquals("outside", outside.readText())
    }

    @Test
    fun zipRoundTripRelocatesPhotosAndPreservesBytes() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val photo = EvidenceStore.store(context, bytes.inputStream())
        val source = meal("meal-1", photo.toString())
        val archive = ByteArrayOutputStream().also {
            DossierExporter.exportBackupZip(context, listOf(source), it)
        }
        val restored = DossierExporter.importBackup(context, archive.toByteArray().inputStream()).single()
        assertNotEquals(source.imageUri, restored.imageUri)
        assertEquals(source, restored.copy(imageUri = source.imageUri))
        val actual = File(checkNotNull(Uri.parse(restored.imageUri).path)).readBytes()
        assertArrayEquals(bytes, actual)
    }

    @Test
    fun traversalArchiveCannotWriteOutsideOrChangeEvidence(): Unit = runBlocking {
        val target = File(context.filesDir, "outside.jpg")
        val rejected = runCatching {
            DossierExporter.importBackup(context, archiveWithUnexpectedEntry("../outside.jpg").inputStream())
        }.exceptionOrNull()
        // Android 14+ can reject traversal before the app reviews ZIP entry names.
        assertTrue(rejected is IllegalArgumentException || rejected is java.util.zip.ZipException)
        assertFalse(target.exists())
        assertTrue(EvidenceStore.directory(context).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun appRejectsUnreferencedPhotoEvenWhenZipPathIsValid(): Unit = runBlocking {
        val rejected = runCatching {
            DossierExporter.importBackup(context, archiveWithUnexpectedEntry("foreign.jpg").inputStream())
        }.exceptionOrNull()
        assertEquals("Unexpected or duplicate backup entry.", rejected?.message)
        assertTrue(EvidenceStore.directory(context).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun archiveExportRejectsOversizedPhotoBeforeWriting(): Unit = runBlocking {
        val photo = File(EvidenceStore.directory(context), "oversized.jpg")
        java.io.RandomAccessFile(photo, "rw").use {
            it.setLength(EvidenceStore.MAX_EVIDENCE_BYTES + 1)
        }
        val archive = ByteArrayOutputStream()
        val rejected = runCatching {
            DossierExporter.exportBackupZip(context, listOf(meal("large", Uri.fromFile(photo).toString())), archive)
        }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)
        assertEquals(0, archive.size())
        assertTrue(photo.exists())
    }

    @Test
    fun legacyJsonRestoresMetadataWithoutForeignUri(): Unit = runBlocking {
        val legacy = meal("legacy", "file:///another-installation/private.jpg")
        val json = DossierExporter.generateJson(listOf(legacy))
        val restored = DossierExporter.importBackup(context, json.byteInputStream()).single()
        assertEquals(legacy.copy(imageUri = null), restored)
    }

    private fun archiveWithUnexpectedEntry(name: String): ByteArray {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"version":2,"count":0,"meals":[]}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(name))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        return archive.toByteArray()
    }

    @Test
    fun conflictingBatchKeepsExistingRowAndReferencedPhoto() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = MealRepository(room.mealDao())
            val current = EvidenceStore.store(context, byteArrayOf(10).inputStream())
            val incoming = EvidenceStore.store(context, byteArrayOf(20).inputStream())
            repo.insertMeal(meal("original", current.toString()))
            try {
                repo.insertMeals(listOf(meal("new", null), meal("original", incoming.toString())))
                fail("Duplicate ID was accepted")
            } catch (_: SQLiteConstraintException) {
                EvidenceStore.reconcileDurable(context, repo.getPhotoReferences().toSet())
            }
            assertNull(repo.getMealById("new"))
            assertEquals(current.toString(), repo.getMealById("original")?.imageUri)
            assertArrayEquals(byteArrayOf(10), File(checkNotNull(current.path)).readBytes())
            assertFalse(File(checkNotNull(incoming.path)).exists())
        } finally {
            room.close()
        }
    }

    @Test
    fun failedDatabaseEraseKeepsLegacyPhotoAndLaterEraseCanFinish() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = MealRepository(room.mealDao())
            val legacy = File(EvidenceStore.directory(context), "old.jpg").apply { writeText("original") }
            repo.insertMeal(meal("old", Uri.fromFile(legacy).toString()))
            EvidenceStore.markAllForDeletion(context)
            withContext(Dispatchers.IO) {
                room.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER fail_erase BEFORE DELETE ON meal_entries " +
                        "BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
                )
            }
            try {
                repo.deleteAllMeals()
                fail("Expected database erase to fail")
            } catch (_: SQLiteConstraintException) {
                EvidenceStore.reconcileDurable(context, repo.getPhotoReferences().toSet()).getOrThrow()
            }
            assertEquals("original", legacy.readText())
            assertNotNull(repo.getMealById("old"))

            withContext(Dispatchers.IO) {
                room.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_erase")
            }
            EvidenceStore.markAllForDeletion(context)
            repo.deleteAllMeals()
            // Simulate a restart after the DB commit and before filesystem cleanup.
            EvidenceStore.reconcileDurable(context, repo.getPhotoReferences().toSet()).getOrThrow()
            assertFalse(legacy.exists())
        } finally {
            room.close()
        }
    }

    @Test
    fun reconciliationKeepsSharedPhotoUntilAllRecordsAreGone() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = MealRepository(room.mealDao())
            val photo = EvidenceStore.store(context, byteArrayOf(1).inputStream())
            val file = File(checkNotNull(photo.path))
            repo.insertMeals(listOf(meal("first", photo.toString()), meal("second", photo.toString())))

            EvidenceStore.markForDeletion(context, photo.toString())
            repo.deleteMeal("first")
            EvidenceStore.reconcileDurable(context, repo.getPhotoReferences().toSet()).getOrThrow()
            assertTrue(file.exists())

            EvidenceStore.markForDeletion(context, photo.toString())
            repo.deleteMeal("second")
            EvidenceStore.reconcileDurable(context, repo.getPhotoReferences().toSet()).getOrThrow()
            assertFalse(file.exists())
        } finally {
            room.close()
        }
    }

    @Test
    fun failedLegacyPhotoRemovalStaysJournaledUntilRetrySucceeds() {
        val blocked = File(EvidenceStore.directory(context), "legacy.jpg").apply { mkdir() }
        val child = File(blocked, "occupied").apply { writeText("fixture") }
        EvidenceStore.markForDeletion(context, Uri.fromFile(blocked).toString())

        assertTrue(EvidenceStore.reconcileDurable(context, emptySet()).isFailure)
        assertTrue(child.delete())

        EvidenceStore.reconcileDurable(context, emptySet()).getOrThrow()
        assertFalse(blocked.exists())
    }

    @Test
    fun originalSchemaUpgradesWithoutLosingMeal(): Unit = runBlocking {
        val name = "migration-" + UUID.randomUUID()
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL(
                    "CREATE TABLE meal_entries (id TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, " +
                        "imageUri TEXT, foodName TEXT NOT NULL, calories INTEGER NOT NULL, proteinGrams REAL NOT NULL, " +
                        "carbsGrams REAL NOT NULL, fatGrams REAL NOT NULL, isLiquid INTEGER NOT NULL)"
                )
                db.execSQL("INSERT INTO meal_entries VALUES ('old', 101, NULL, 'Soup', 67, 1.0, 2.0, 3.0, 1)")
                db.version = 1
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_1_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
                .build()
            try {
                val migrated = MealRepository(db.mealDao()).getMealById("old")
                assertEquals("Soup", migrated?.foodName)
                assertEquals(67, migrated?.calories)
                assertEquals(true, migrated?.isLiquid)
                assertEquals(false, migrated?.isNightRefueling)
                withContext(Dispatchers.IO) {
                    db.openHelper.writableDatabase.query("PRAGMA index_list(meal_entries)").use {
                        assertTrue(generateSequence { if (it.moveToNext()) it.getString(1) else null }
                            .any { index -> index == "index_meal_entries_timestamp" })
                    }
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun localVersionSevenUpgradesWithMealsAndAuditHistory() = runBlocking {
        verifyVersionSevenUpgrade("760eb363f077ac5cbdc47cd39c4f4ee8", hasAuditIndex = false)
    }

    @Test
    fun publishedVersionSevenUpgradesWithMealsAndAuditHistory() = runBlocking {
        verifyVersionSevenUpgrade("ed5399b3d1461132ea58c4fa276d7f67", hasAuditIndex = true)
    }

    private suspend fun verifyVersionSevenUpgrade(identityHash: String, hasAuditIndex: Boolean) {
        val name = "migration-v7-" + UUID.randomUUID()
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL(
                    "CREATE TABLE meal_entries (id TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, " +
                        "imageUri TEXT, foodName TEXT NOT NULL, calories INTEGER NOT NULL, proteinGrams REAL NOT NULL, " +
                        "carbsGrams REAL NOT NULL, fatGrams REAL NOT NULL, isLiquid INTEGER NOT NULL, " +
                        "latitude REAL, longitude REAL, assessment TEXT, isRestricted INTEGER NOT NULL, " +
                        "isNightRefueling INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX index_meal_entries_timestamp ON meal_entries (timestamp)")
                db.execSQL(
                    "CREATE TABLE audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, category TEXT NOT NULL, message TEXT NOT NULL)"
                )
                if (hasAuditIndex) db.execSQL("CREATE INDEX index_audit_log_timestamp ON audit_log (timestamp)")
                db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                db.execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(identityHash))
                db.execSQL(
                    "INSERT INTO meal_entries VALUES " +
                        "('fixture', 101, NULL, 'Soup', 67, 1.0, 2.0, 3.0, 1, NULL, NULL, NULL, 0, 0)"
                )
                db.execSQL("INSERT INTO audit_log VALUES (1, 102, 'DATA_INGEST', 'saved meal')")
                db.version = 7
            }
            val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_7_8)
                .build()
            try {
                assertEquals("Soup", MealRepository(room.mealDao()).getMealById("fixture")?.foodName)
                assertEquals("saved meal", room.auditDao().getRecentAudits().first().single().message)
                withContext(Dispatchers.IO) {
                    room.openHelper.writableDatabase.query("PRAGMA index_list(audit_log)").use {
                        assertTrue(it.moveToFirst())
                        assertEquals("index_audit_log_timestamp", it.getString(1))
                    }
                }
            } finally {
                room.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun androidKeyStoreRoundTripAndTamperFailure() {
        val plaintext = "fake-instrumentation-token"
        val encrypted = KeyStoreCrypto.encrypt(plaintext)
        assertNotEquals(plaintext, encrypted)
        assertEquals(plaintext, KeyStoreCrypto.decrypt(encrypted))
        assertEquals("", KeyStoreCrypto.decrypt(encrypted.take(10) + "!"))
    }

    private fun meal(id: String, uri: String?) = MealEntry(
        id = id,
        timestamp = 1234L,
        imageUri = uri,
        foodName = "Soup",
        calories = 67,
        proteinGrams = 1f,
        carbsGrams = 2f,
        fatGrams = 3f,
        isLiquid = true
    )
}
