package com.sharek.macromandate

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuildSecurityConfigurationTest {
    private fun projectFile(path: String): File =
        sequenceOf(File(path), File("..", path)).first { it.exists() }

    @Test
    fun manifest_cameraFeature_isOptional() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val cameraFeature = Regex(
            """<uses-feature[^>]*android:name="android\.hardware\.camera\.any"[^>]*>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(manifest)?.value.orEmpty()
        assertTrue(cameraFeature.contains("android:required=\"false\""))
    }

    @Test
    fun releaseSecretGuard_unrelatedFailure_doesNotCountAsPass() {
        val workflow = projectFile(".github/workflows/android.yml").readText()
        assertTrue(workflow.contains("MM_RELEASE_EMBEDDED_API_KEY_FORBIDDEN"))
        assertTrue(workflow.contains("Gradle failed for an unrelated reason"))
    }

    @Test
    fun export_withoutActiveFlow_readsRoomDirectly() {
        val manager = projectFile("app/src/main/java/com/sharek/macromandate/domain/BackupManager.kt").readText()
        assertTrue(manager.contains("mealRepository.getAllMealsSnapshot()"))
        assertTrue(!manager.contains("mealEntries.value"))
    }

    @Test
    fun export_afterRestore_usesFreshDatabaseSnapshot() {
        val manager = projectFile("app/src/main/java/com/sharek/macromandate/domain/BackupManager.kt").readText()
        assertTrue(manager.windowed("mealRepository.getAllMealsSnapshot()".length).count {
            it == "mealRepository.getAllMealsSnapshot()"
        } >= 2)
    }

    @Test
    fun evidencePersist_failure_doesNotSilentlyCreateSuccessfulMeal() {
        val viewModel = projectFile("app/src/main/java/com/sharek/macromandate/viewmodel/MainViewModel.kt").readText()
        val failureGuard = viewModel.indexOf("if (persisted == null)")
        val mealConstruction = viewModel.indexOf("val entry = MealEntry", startIndex = failureGuard)
        assertTrue(failureGuard >= 0 && mealConstruction > failureGuard)
    }

    @Test
    fun cameraCapture_noApiKey_doesNotLeaveOrphan() {
        val viewModel = projectFile("app/src/main/java/com/sharek/macromandate/viewmodel/MainViewModel.kt").readText()
        val captureFlow = viewModel.substringAfter("fun processImageForMacros")
        val noKey = captureFlow.substringAfter("if (apiKey.isBlank())").substringBefore("_uiState.value = UiState.Loading")
        assertTrue(noKey.contains("releaseCapture(uri)"))
    }

    @Test
    fun cameraCapture_analysisFailure_doesNotLeaveOrphan() {
        val viewModel = projectFile("app/src/main/java/com/sharek/macromandate/viewmodel/MainViewModel.kt").readText()
        assertTrue(viewModel.contains("failAnalysis(uri, error.analysisError)"))
        assertTrue(viewModel.substringAfter("private fun failAnalysis").contains("releaseCapture(source)"))
    }

    @Test
    fun clearActivityLog_matchesDocumentedPostcondition() {
        val viewModel = projectFile("app/src/main/java/com/sharek/macromandate/viewmodel/MainViewModel.kt").readText()
        val body = viewModel.substringAfter("fun clearActivityLog()").substringBefore("fun resetUiState")
        assertTrue(body.contains("clearAllAudits()"))
        assertTrue(!body.contains("logAudit("))
    }
}
