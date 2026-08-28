package com.sharek.macromandate.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restore is the app's other untrusted boundary.
 *
 * The file comes from a document picker, so it is arbitrary bytes chosen by
 * whoever hands it over — a corrupted export, a hand-edited archive, or a file
 * crafted to write values the UI would never accept. Restore previously took
 * every field verbatim, which made it a way around all of the entry validation.
 */
class BackupRestoreHostileInputTest {

    private fun archive(meals: String, version: Int = 1) =
        """{"version":$version,"exportedAt":1700000000000,"meals":[$meals]}"""

    // ---- structural rejection ----------------------------------------------

    @Test
    fun aNewerArchiveVersionIsRefusedRatherThanPartiallyRead() = runBlocking {
        // Reading a future file with today's parser drops fields it does not know
        // about, and reports success — which looks like a restore that lost data.
        val result = DossierExporter.parseJsonBackup(archive("", version = 99))
        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as? DossierExporter.RestoreException)?.error
        assertTrue(error is DossierExporter.RestoreError.UnsupportedVersion)
    }

    @Test
    fun anArchiveWithoutAMealsArrayIsRefused() = runBlocking {
        assertTrue(DossierExporter.parseJsonBackup("""{"version":1}""").isFailure)
    }

    @Test
    fun anOversizedFileIsRefusedBeforeParsing() = runBlocking {
        val huge = "x".repeat(DossierExporter.MAX_BACKUP_CHARS + 1)
        val result = DossierExporter.parseJsonBackup(huge)
        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as? DossierExporter.RestoreException)?.error
                is DossierExporter.RestoreError.TooLarge
        )
    }

    @Test
    fun anEntryThatIsNotAnObjectIsSkippedRatherThanAbortingTheRestore() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive(""""not an object", {"id":"a","foodName":"Real","calories":100}""")
        ).getOrThrow()
        assertEquals(1, meals.size)
        assertEquals("Real", meals[0].foodName)
    }

    // ---- value clamping ------------------------------------------------------

    @Test
    fun impossibleNutritionValuesAreClampedOnRestore() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"Injected","calories":9999999,"proteinGrams":-40,"fatGrams":1e12}""")
        ).getOrThrow()
        assertEquals(NutritionBounds.MAX_CALORIES, meals[0].calories)
        assertEquals(0f, meals[0].proteinGrams, 0.001f)
        assertEquals(NutritionBounds.MAX_MACRO_GRAMS, meals[0].fatGrams, 0.001f)
    }

    @Test
    fun aFarFutureTimestampIsClampedToNow() = runBlocking {
        // "Today" is an open-ended `timestamp >= startOfDay` query, so one row
        // dated in 2099 would be counted in every daily total from now on.
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"Future","timestamp":4102444800000}""")
        ).getOrThrow()
        assertTrue(meals[0].timestamp <= System.currentTimeMillis())
    }

    @Test
    fun anAbsurdlyOldTimestampIsClampedForward() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"Ancient","timestamp":-99999999999999}""")
        ).getOrThrow()
        assertTrue(meals[0].timestamp > 0L)
    }

    @Test
    fun outOfRangeCoordinatesAreDroppedNotStored() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"Nowhere","latitude":999.0,"longitude":-5000.0}""")
        ).getOrThrow()
        assertNull(meals[0].latitude)
        assertNull(meals[0].longitude)
    }

    @Test
    fun validCoordinatesSurvive() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"Cafe","latitude":51.5074,"longitude":-0.1278}""")
        ).getOrThrow()
        assertEquals(51.5074, meals[0].latitude!!, 0.0001)
        assertEquals(-0.1278, meals[0].longitude!!, 0.0001)
    }

    @Test
    fun overlongStringsAreTruncatedOnRestore() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"${"Z".repeat(50_000)}","assessment":"${"Y".repeat(50_000)}"}""")
        ).getOrThrow()
        assertEquals(NutritionBounds.MAX_NAME_LENGTH, meals[0].foodName.length)
        assertEquals(NutritionBounds.MAX_ASSESSMENT_LENGTH, meals[0].assessment?.length)
    }

    // ---- image URI containment ----------------------------------------------

    @Test
    fun aForeignImageUriIsDroppedRatherThanStored() = runBlocking {
        // Deletion acts on the stored URI, so a record pointing outside the
        // evidence directory turns "delete this meal" into an arbitrary unlink.
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"X","imageUri":"content://com.other.app/secret/1"}""")
        ).getOrThrow()
        assertNull(meals[0].imageUri)
    }

    @Test
    fun aTraversingImageUriIsDropped() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"X","imageUri":"file:///data/data/pkg/files/evidence/../../databases/macro_mandate_db"}""")
        ).getOrThrow()
        assertNull(meals[0].imageUri)
    }

    @Test
    fun anEvidenceStoreUriIsPreserved() = runBlocking {
        val uri = "file:///data/user/0/com.sharek.macromandate/files/evidence/abc.jpg"
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"id":"a","foodName":"X","imageUri":"$uri"}""")
        ).getOrThrow()
        assertEquals(uri, meals[0].imageUri)
    }

    // ---- deduplication -------------------------------------------------------

    @Test
    fun duplicateIdsCollapseDeterministicallyToTheFirst() = runBlocking {
        // Room's REPLACE strategy would otherwise let the *last* duplicate win,
        // so a restore's reported count would exceed the rows actually stored.
        val meals = DossierExporter.parseJsonBackup(
            archive(
                """{"id":"dup","foodName":"First","calories":100},""" +
                    """{"id":"dup","foodName":"Second","calories":200}"""
            )
        ).getOrThrow()
        assertEquals(1, meals.size)
        assertEquals("First", meals[0].foodName)
    }

    @Test
    fun entriesWithoutIdsGetDistinctGeneratedOnes() = runBlocking {
        val meals = DossierExporter.parseJsonBackup(
            archive("""{"foodName":"A"},{"foodName":"B"}""")
        ).getOrThrow()
        assertEquals(2, meals.size)
        assertTrue(meals[0].id != meals[1].id)
    }

    @Test
    fun restoringTheSameArchiveTwiceIsIdempotent() = runBlocking {
        val json = archive("""{"id":"stable","foodName":"Same","calories":100}""")
        val first = DossierExporter.parseJsonBackup(json).getOrThrow()
        val second = DossierExporter.parseJsonBackup(json).getOrThrow()
        // Same ids both times, so a second restore updates rather than duplicates.
        assertEquals(first.map { it.id }, second.map { it.id })
    }
}
