package com.sharek.macromandate.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.graphics.scale
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharek.macromandate.BuildConfig
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.local.AuditEntity
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.data.repository.AuditRepository
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.network.AnalysisError
import com.sharek.macromandate.network.ApiConfig
import com.sharek.macromandate.network.ChatMessage
import com.sharek.macromandate.network.ChatRequest
import com.sharek.macromandate.network.ContentPart
import com.sharek.macromandate.network.HuggingFaceApi
import com.sharek.macromandate.util.DossierExporter
import com.sharek.macromandate.util.DossierReportGenerator
import com.sharek.macromandate.util.NutritionBounds
import com.sharek.macromandate.util.NutritionSanitizer
import com.sharek.macromandate.util.ParsedNutrition
import com.sharek.macromandate.util.ComplianceEngine
import com.sharek.macromandate.util.EvidenceStore
import com.sharek.macromandate.widget.MandateWidget
import com.sharek.macromandate.util.ImageForensics
import com.sharek.macromandate.worker.EnforcementScheduler
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.sharek.macromandate.ui.theme.TerminalTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.abs

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val mealName: String) : UiState()
    data class Error(val message: String) : UiState()
}

/**
 * How today's intake sits against the configured target.
 *
 * This is a *label*, not a gate. An earlier design let these values disable the
 * gallery picker, cover the meal detail screen, block CSV/JSON export, and — at
 * the bottom of the scale — replace the entire app with a screen that asked a
 * language model whether to erase the user's log. Distance from a calorie target
 * is not grounds for withholding someone's own records, and a model verdict is
 * not grounds for deleting them. The status now only ever changes what is
 * *said*, never what is *reachable*.
 */
enum class ComplianceStatus {
    EXEMPLARY, ACCEPTABLE, SUBVERSIVE, CRISIS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MealRepository
    private val auditRepository: AuditRepository
    private val preferences: MandatePreferences

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MealRepository(database.mealDao())
        auditRepository = AuditRepository(database.auditDao())
        preferences = MandatePreferences(application)

        logAudit("SYSTEM_BOOT", "SURVEILLANCE TERMINAL INITIALIZED.")
    }

    val mealEntries: StateFlow<List<MealEntry>> = repository.getAllMeals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentAudits: StateFlow<List<AuditEntity>> = auditRepository.getRecentAudits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayMeals: StateFlow<List<MealEntry>> = repository.getTodayMeals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val weeklyMeals: StateFlow<List<MealEntry>> = repository.getWeeklyMeals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val calorieTarget: StateFlow<Int> = preferences.calorieTargetFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 2500
        )

    val enforcementEnabled: StateFlow<Boolean> = preferences.enforcementEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val locationTrackingEnabled: StateFlow<Boolean> = preferences.locationTrackingEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** True when analysis can run — a key was entered in Settings or baked in at build time. */
    val hasApiKey: StateFlow<Boolean> = preferences.apiKeyFlow
        .map { it.isNotBlank() || ApiConfig.buildTimeKey.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ApiConfig.buildTimeKey.isNotBlank()
        )

    /** Masked for display so the panel can confirm a key exists without revealing it. */
    val apiKeyHint: StateFlow<String> = preferences.apiKeyFlow
        .map { key -> if (key.isBlank()) "" else "•".repeat(8) + key.takeLast(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val terminalTheme: StateFlow<TerminalTheme> = preferences.terminalThemeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TerminalTheme.CYBER_CYAN
        )

    fun updateTerminalTheme(theme: TerminalTheme) {
        viewModelScope.launch {
            preferences.updateTerminalTheme(theme)
            logAudit("CONFIG", "TERMINAL THEME SET TO ${theme.displayName}.")
        }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            preferences.updateApiKey(key)
            logAudit("CONFIG", if (key.isBlank()) "API key cleared." else "API key saved.")
        }
    }

    /** Key entered in Settings wins; the build-time value is only a dev fallback. */
    private suspend fun resolveApiKey(): String =
        preferences.apiKeyFlow.first().ifBlank { ApiConfig.buildTimeKey }

    val complianceScore: StateFlow<Int> = combine(weeklyMeals, calorieTarget) { meals, target ->
        calculateComplianceScore(meals, target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    /**
     * The status shown in the dashboard banner.
     *
     * Previously this subtracted a 40-point penalty for a "restricted zone" meal
     * and 15 for eating between 23:00 and 05:00 — either enough on its own to
     * push someone into the state that used to lock the app. Eating late is not
     * a defect, and the app has no evidence base for treating it as one, so the
     * score now reflects only the thing the user actually configured: distance
     * from their calorie target.
     */
    val complianceStatus: StateFlow<ComplianceStatus> = complianceScore
        .map { ComplianceEngine.statusFor(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComplianceStatus.EXEMPLARY)

    /**
     * Forbidden sectors are intentionally empty.
     *
     * This previously shipped mock coordinates for central Berlin and Manhattan.
     * A real user eating at either location was flagged for a restricted-zone
     * violation and given a 40-point compliance penalty — on its own enough to
     * drop them into CRISIS, which replaces the whole app with the leniency
     * screen. Until real sector data exists, nothing is restricted.
     */
    private val forbiddenSectors: List<Pair<Double, Double>> = emptyList()

    private fun checkForbiddenSectors(lat: Double, lng: Double): Boolean =
        forbiddenSectors.any { (fLat, fLng) ->
            abs(lat - fLat) < 0.005 && abs(lng - fLng) < 0.005 // Approx 500m
        }

    private fun calculateComplianceScore(meals: List<MealEntry>, dailyTarget: Int): Int =
        ComplianceEngine.calculateScore(meals, dailyTarget)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _dailyBriefing = MutableStateFlow<String?>(null)
    val dailyBriefing: StateFlow<String?> = _dailyBriefing.asStateFlow()

    fun generateDailyBriefing() {
        viewModelScope.launch {
            val meals = todayMeals.value
            if (meals.isEmpty()) {
                _uiState.value = UiState.Error("NO DATA AVAILABLE FOR SYNTHESIS.")
                return@launch
            }
            val apiKey = resolveApiKey()
            if (apiKey.isBlank()) {
                _uiState.value = UiState.Error(ApiConfig.NOT_CONFIGURED_MESSAGE)
                return@launch
            }

            _uiState.value = UiState.Loading
            try {
                val totals = "Total: ${meals.sumOf { it.calories }} kcal, " +
                        "${meals.sumOf { it.proteinGrams.toDouble() }.toInt()}P, " +
                        "${meals.sumOf { it.carbsGrams.toDouble() }.toInt()}C, " +
                        "${meals.sumOf { it.fatGrams.toDouble() }.toInt()}F."
                val mealNames = meals.joinToString(", ") { it.foodName }

                // The tone no longer escalates with how far the user is from
                // target. It previously asked the model for "EXTREME CORRECTION
                // REQUIRED. AGGRESSIVE TONE." and, at the bottom of the scale,
                // "TERMINAL WARNING. ABSOLUTE CONDEMNATION." — an open-ended
                // instruction to a language model to condemn someone for what
                // they ate, in an app that is not qualified to judge it. The
                // clipped terminal register stays; the escalating hostility does
                // not.
                val prompt = "Summarize these meals as a short, factual daily briefing in a clipped, " +
                        "cold, military-terminal register. Data: $totals. Items: $mealNames. " +
                        "Describe what was logged and how the totals compare to nothing in particular. " +
                        "Do not evaluate the person, moralize about the food, or give health, dietary or " +
                        "medical advice. Two or three sentences. Return only the briefing text."

                val response = api.chatCompletion(
                    token = ApiConfig.authHeader(apiKey),
                    request = textRequest(prompt)
                )

                if (response.isSuccessful) {
                    _dailyBriefing.value = response.body()?.firstMessage().orEmpty().trim()
                    logAudit("INTEL_SYNTHESIS", "DAILY BRIEFING GENERATED.")
                    // Only the success path returns to Idle. A `finally` here would
                    // overwrite the Error below before any collector could observe it
                    // — StateFlow conflates, and there is no suspension point between.
                    _uiState.value = UiState.Idle
                } else {
                    _uiState.value = UiState.Error("UPLINK FAILURE: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("SYNTHESIS ERROR: ${e.localizedMessage?.uppercase()}")
            }
        }
    }

    fun dismissBriefing() {
        _dailyBriefing.value = null
    }

    fun updateCalorieTarget(target: Int) {
        viewModelScope.launch {
            preferences.updateCalorieTarget(target)
            logAudit("MANDATE_SHIFT", "TARGET ADJUSTED TO $target KCAL.")
            updateWidget()
        }
    }

    fun toggleLocationTracking(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateLocationTrackingEnabled(enabled)
            logAudit(
                "PRIVACY",
                "GEOSPATIAL TRACKING ${if (enabled) "AUTHORIZED BY SUBJECT" else "REVOKED BY SUBJECT"}."
            )
        }
    }

    fun toggleEnforcement(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateEnforcementEnabled(enabled)
            if (enabled) {
                EnforcementScheduler.schedule(getApplication())
            } else {
                EnforcementScheduler.cancel(getApplication())
            }
            logAudit("ENFORCEMENT", "SURVEILLANCE PROTOCOL ${if (enabled) "ENABLED" else "DISABLED"}.")
        }
    }

    /**
     * Generates the dossier and writes it to [uri]. The write stays on the IO
     * dispatcher: handing the CSV back to a main-thread callback made the caller
     * do blocking SAF I/O on the UI thread.
     */
    fun exportDataTo(
        context: android.content.Context,
        uri: Uri,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                try {
                    val csv = DossierExporter.generateCsv(mealEntries.value)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray())
                        true
                    } ?: false
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Dossier export failed", e)
                    false
                }
            }
            if (succeeded) {
                logAudit("DATA_EXPORT", "DOSSIER EXFILTRATED.")
            } else {
                logAudit("DATA_EXPORT", "DOSSIER EXFILTRATION FAILED.")
            }
            onResult(succeeded)
        }
    }

    fun exportJsonBackupTo(
        context: android.content.Context,
        uri: Uri,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                try {
                    val json = DossierExporter.generateJson(mealEntries.value)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                } catch (e: Exception) {
                    Log.e("MainViewModel", "JSON backup export failed", e)
                    false
                }
            }
            if (succeeded) {
                logAudit("DATA_BACKUP", "FULL DATABASE BACKUP EXPORTED (${mealEntries.value.size} RECORDS).")
            } else {
                logAudit("DATA_BACKUP", "DATABASE BACKUP EXPORT FAILED.")
            }
            onResult(succeeded)
        }
    }

    fun importJsonBackupFrom(
        context: android.content.Context,
        uri: Uri,
        onResult: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            val result: Result<Int> = withContext(Dispatchers.IO) {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: return@withContext Result.failure(Exception("Failed to read selected backup file."))

                    val meals = DossierExporter.parseJsonBackup(jsonString).getOrElse { failure ->
                        return@withContext Result.failure(Exception(restoreMessage(failure)))
                    }

                    if (meals.isNotEmpty()) {
                        // Room wraps a list insert in a single transaction, so a
                        // failure part-way leaves the log as it was rather than
                        // half-restored.
                        repository.insertMeals(meals)
                    }
                    Result.success(meals.size)
                } catch (e: OutOfMemoryError) {
                    Result.failure(Exception("That backup file is too large to restore."))
                } catch (e: Exception) {
                    Log.w(TAG, "JSON backup import failed", e)
                    Result.failure(Exception("That file could not be read as a MacroMandate backup."))
                }
            }

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                logAudit("DATA_RESTORE", "RESTORED $count MEAL RECORDS FROM BACKUP.")
            } else {
                logAudit("DATA_RESTORE", "DATABASE RESTORE FAILED: ${result.exceptionOrNull()?.message}")
            }
            onResult(result)
        }
    }

    fun generateWeeklyReport(): String {
        return DossierReportGenerator.generateWeeklyMarkdown(
            meals = weeklyMeals.value,
            calorieTarget = calorieTarget.value,
            complianceScore = complianceScore.value,
            complianceStatus = complianceStatus.value
        )
    }

    fun exportReportTo(
        context: android.content.Context,
        uri: Uri,
        reportText: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(reportText.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Weekly report export failed", e)
                    false
                }
            }
            if (succeeded) {
                logAudit("DATA_EXPORT", "WEEKLY DEBRIEF EXPORTED.")
            } else {
                logAudit("DATA_EXPORT", "WEEKLY DEBRIEF EXPORT FAILED.")
            }
            onResult(succeeded)
        }
    }

    private val _pendingAnalysis = MutableStateFlow<PendingAnalysis?>(null)

    /** A model result awaiting the user's confirmation. Nothing is stored until they accept it. */
    val pendingAnalysis: StateFlow<PendingAnalysis?> = _pendingAnalysis.asStateFlow()

    private var analysisJob: Job? = null

    /**
     * The frame the running analysis is working on, so [cancelAnalysis] can
     * delete it. Cleared once the result is confirmed or released.
     */
    private var inFlightCapture: Uri? = null

    /**
     * Sends one image for analysis and parks the result in [pendingAnalysis].
     *
     * Nothing is written to the meal log here, and the image is not copied into
     * the evidence store yet: both happen in [confirmPendingAnalysis], so backing
     * out of a bad result leaves nothing behind.
     */
    fun processImageForMacros(uri: Uri, context: android.content.Context) {
        // A second capture supersedes the first rather than racing it into the log.
        analysisJob?.cancel()
        inFlightCapture?.takeIf { it != uri }?.let { releaseCapture(it) }
        inFlightCapture = uri
        analysisJob = viewModelScope.launch {
            val apiKey = resolveApiKey()
            if (apiKey.isBlank()) {
                _uiState.value = UiState.Error(AnalysisError.NoApiKey.message)
                return@launch
            }
            _uiState.value = UiState.Loading

            val capturedAt = System.currentTimeMillis()

            // Coordinates are read only when the user has opted in. They are
            // watermarked onto the frame that gets uploaded, so this is the point
            // at which location leaves the device.
            val location = if (!preferences.locationTrackingEnabledFlow.first()) {
                null
            } else {
                lastKnownLocation(context)
            }

            val base64Image = withContext(Dispatchers.IO) {
                val watermarkedUri = if (location != null) {
                    ImageForensics.watermarkImage(
                        context = context,
                        uri = uri,
                        id = UUID.randomUUID().toString(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = capturedAt
                    )
                } else null
                try {
                    uriToScaledBase64(watermarkedUri ?: uri, context)
                } finally {
                    // Transient analysis artefact; the record keeps the original frame.
                    watermarkedUri?.path?.let { path -> File(path).delete() }
                }
            }

            if (base64Image == null) {
                failAnalysis(uri, AnalysisError.ImageUnreadable)
                return@launch
            }

            val parsed = try {
                requestNutrition(apiKey, base64Image)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Analysis request failed", e)
                failAnalysis(uri, AnalysisError.fromThrowable(e))
                return@launch
            }

            parsed.fold(
                onSuccess = { nutrition ->
                    _pendingAnalysis.value = PendingAnalysis(
                        sourceImage = uri,
                        nutrition = nutrition,
                        capturedAt = capturedAt,
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    )
                    // Ownership passes to the pending result; discard/confirm
                    // decides what happens to the file from here.
                    inFlightCapture = null
                    _uiState.value = UiState.Idle
                },
                onFailure = { error ->
                    failAnalysis(uri, (error as? AnalysisFailure)?.error ?: AnalysisError.Unknown)
                }
            )
        }
    }

    private class AnalysisFailure(val error: AnalysisError) : Exception(error.message)

    /** Performs the request and extracts one nutrition object, or an [AnalysisError]. */
    private suspend fun requestNutrition(apiKey: String, base64Image: String): Result<ParsedNutrition> {
        val response = api.chatCompletion(
            token = ApiConfig.authHeader(apiKey),
            request = imageRequest(ANALYSIS_PROMPT, base64Image)
        )

        if (!response.isSuccessful) {
            // The body can echo the request or carry a provider HTML page, so it
            // is never shown to the user and never logged in a release build.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Provider returned ${response.code()}: ${response.errorBody()?.string()}")
            }
            return Result.failure(AnalysisFailure(AnalysisError.fromHttpStatus(response.code())))
        }

        val responseText = response.body()?.firstMessage().orEmpty()
        val jsonStart = responseText.indexOf('{')
        val jsonEnd = responseText.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd <= jsonStart) {
            // Prose, markdown fences and truncated replies all land here.
            if (BuildConfig.DEBUG) Log.d(TAG, "No JSON object in reply: $responseText")
            return Result.failure(AnalysisFailure(AnalysisError.UnreadableResult))
        }

        return try {
            Result.success(
                NutritionSanitizer.parseAndSanitize(responseText.substring(jsonStart, jsonEnd + 1))
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Unparsable nutrition object", e)
            Result.failure(AnalysisFailure(AnalysisError.UnreadableResult))
        }
    }

    /**
     * Commits a reviewed analysis to the log, with whatever corrections the user
     * made. The image is copied into internal storage at this point: a
     * photo-picker grant does not survive process death.
     */
    fun confirmPendingAnalysis(corrected: ParsedNutrition) {
        val pending = _pendingAnalysis.value ?: return
        _pendingAnalysis.value = null
        viewModelScope.launch {
            val context = getApplication<Application>()
            val mealId = UUID.randomUUID().toString()
            val storedUri = withContext(Dispatchers.IO) {
                EvidenceStore.persist(context, pending.sourceImage, mealId)
            }

            val entry = MealEntry(
                id = mealId,
                timestamp = pending.capturedAt,
                imageUri = storedUri?.toString(),
                foodName = NutritionBounds.clampName(corrected.foodName, DEFAULT_MEAL_NAME),
                calories = NutritionBounds.clampCalories(corrected.calories),
                proteinGrams = NutritionBounds.clampGrams(corrected.proteinGrams),
                carbsGrams = NutritionBounds.clampGrams(corrected.carbsGrams),
                fatGrams = NutritionBounds.clampGrams(corrected.fatGrams),
                isLiquid = corrected.isLiquid,
                latitude = pending.latitude,
                longitude = pending.longitude,
                assessment = NutritionBounds.clampAssessment(corrected.assessment),
                isRestricted = false,
                isNightRefueling = isLateNight(pending.capturedAt)
            )

            repository.insertMeal(entry)
            logAudit("DATA_INGEST", "RECORD LOGGED: ${entry.foodName.uppercase()}")
            updateWidget()
            _uiState.value = UiState.Success(entry.foodName)
        }
    }

    /**
     * Drops a result without recording it, and deletes the frame it came from.
     *
     * A camera capture is written straight into the evidence store so that the
     * meal record can point at durable storage. That means an analysis the user
     * declines would otherwise leave its photograph on disk permanently, with no
     * meal referencing it and no way to reach it from the UI — the app would
     * accumulate pictures of food the user explicitly chose not to keep.
     *
     * Gallery selections are content URIs this app does not own, so nothing is
     * deleted for them.
     */
    fun discardPendingAnalysis() {
        val discarded = _pendingAnalysis.value
        _pendingAnalysis.value = null
        _uiState.value = UiState.Idle
        discarded?.let { releaseCapture(it.sourceImage) }
    }

    /** Cancels an in-flight analysis; cancelling the coroutine cancels the HTTP call. */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        inFlightCapture?.let { releaseCapture(it) }
        inFlightCapture = null
        _uiState.value = UiState.Idle
    }

    /** Reports a failure and releases the frame that was being analysed. */
    private fun failAnalysis(source: Uri, error: AnalysisError) {
        _uiState.value = UiState.Error(error.message)
        releaseCapture(source)
    }

    /** Deletes an unconfirmed capture, if this app owns the file. */
    private fun releaseCapture(uri: Uri) {
        inFlightCapture = null
        viewModelScope.launch(Dispatchers.IO) {
            EvidenceStore.delete(getApplication(), uri.toString())
        }
    }

    private suspend fun lastKnownLocation(context: android.content.Context): android.location.Location? =
        withContext(Dispatchers.IO) {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                // Bounded: an unqualified await blocks this thread indefinitely if
                // Play Services never settles the task.
                Tasks.await(client.lastLocation, LOCATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (_: SecurityException) {
                null
            } catch (_: TimeoutException) {
                null
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Decodes, uprights, downscales and JPEG-encodes an image for analysis.
     *
     * Sized for what a vision model actually uses: an 800 px long edge at quality
     * 80 is roughly 100-200 KB, where the original frame is 3-12 MB. Sending the
     * full sensor image would cost the user's data and the provider's latency for
     * detail the model discards.
     */
    private fun uriToScaledBase64(uri: Uri, context: android.content.Context): String? {
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            // Decoded with inSampleSize so a full-resolution frame is never
            // materialized, and rotated upright so the model is not shown a
            // sideways plate.
            decoded = ImageForensics.decodeUpright(context, uri, maxDimension = 1600) ?: return null

            val longestEdge = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
            val scale = ANALYSIS_MAX_EDGE_PX.toFloat() / longestEdge
            scaled = if (scale < 1f) {
                decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt())
            } else {
                decoded
            }

            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, ANALYSIS_JPEG_QUALITY, output)
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare the image for analysis", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory preparing the image for analysis")
            null
        } finally {
            // The intermediate was never released, so each capture left a
            // full-size bitmap for the collector to find.
            if (scaled !== decoded) scaled?.recycle()
            decoded?.recycle()
        }
    }

    fun addMealEntry(entry: MealEntry) {
        viewModelScope.launch {
            repository.insertMeal(entry)
            updateWidget()
        }
    }

    /**
     * Deletes a meal and the photo stored for it.
     *
     * The image URI has to be read *before* the row goes away: this previously
     * passed the meal id to [EvidenceStore.delete], which expects a URI, so the
     * call silently matched nothing and every photo survived the deletion of its
     * meal. The audit line said "RECORD EXPUNGED" while the picture stayed on
     * disk indefinitely.
     */
    fun deleteMealEntry(id: String) {
        viewModelScope.launch {
            val imageUri = mealEntries.value.firstOrNull { it.id == id }?.imageUri
            repository.deleteMeal(id)
            logAudit("DATA_PURGE", "RECORD EXPUNGED.")
            withContext(Dispatchers.IO) {
                EvidenceStore.delete(getApplication(), imageUri)
            }
            updateWidget()
        }
    }

    fun updateMealEntry(updatedMeal: MealEntry) {
        viewModelScope.launch {
            repository.updateMeal(
                updatedMeal.copy(
                    foodName = NutritionBounds.clampName(updatedMeal.foodName, DEFAULT_MEAL_NAME),
                    calories = NutritionBounds.clampCalories(updatedMeal.calories),
                    proteinGrams = NutritionBounds.clampGrams(updatedMeal.proteinGrams),
                    carbsGrams = NutritionBounds.clampGrams(updatedMeal.carbsGrams),
                    fatGrams = NutritionBounds.clampGrams(updatedMeal.fatGrams)
                )
            )
            logAudit("DATA_CORRECTION", "RECORD ${updatedMeal.id.take(8).uppercase()} MODIFIED.")
            updateWidget()
        }
    }

    fun logManualMeal(
        foodName: String,
        calories: Int,
        protein: Float,
        carbs: Float,
        fat: Float,
        isLiquid: Boolean
    ) {
        viewModelScope.launch {
            val mealId = UUID.randomUUID().toString()
            val loggedAt = System.currentTimeMillis()

            val entry = MealEntry(
                id = mealId,
                timestamp = loggedAt,
                imageUri = null,
                foodName = NutritionBounds.clampName(foodName, DEFAULT_MEAL_NAME),
                calories = NutritionBounds.clampCalories(calories),
                proteinGrams = NutritionBounds.clampGrams(protein),
                carbsGrams = NutritionBounds.clampGrams(carbs),
                fatGrams = NutritionBounds.clampGrams(fat),
                isLiquid = isLiquid,
                latitude = null,
                longitude = null,
                // A meal the user typed in themselves needs no verdict attached to
                // it. This used to record "CIRCADIAN DISCIPLINE BREACH" for
                // anything logged after 23:00 — a judgement the app has no basis
                // for making, stored permanently on the record.
                assessment = null,
                isRestricted = false,
                isNightRefueling = isLateNight(loggedAt)
            )

            repository.insertMeal(entry)
            logAudit("DATA_INGEST", "MANUAL RECORD LOGGED: ${entry.foodName.uppercase()}")
            updateWidget()
            _uiState.value = UiState.Success(entry.foodName)
        }
    }

    /**
     * Erases every meal, every stored photo, and the activity log.
     *
     * There was previously no way to do this short of uninstalling: meals deleted
     * one at a time, the activity log cleared separately, and the photographs
     * stayed on disk regardless (see [deleteMealEntry]). Someone who wants their
     * food and location history gone should not have to trust that deleting rows
     * one by one got all of it.
     *
     * Settings (target, theme, reminders, location) and the API key are left
     * alone: this is a data erase, not a factory reset, and silently clearing a
     * pasted credential would be its own surprise.
     */
    fun deleteAllData(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val succeeded = runCatching {
                repository.deleteAllMeals()
                withContext(Dispatchers.IO) {
                    EvidenceStore.deleteAll(getApplication())
                }
                auditRepository.clearAllAudits()
            }.isSuccess

            // Logged after the purge so the entry survives it, and deliberately
            // recorded rather than left silent: an erase is worth a trace.
            logAudit("DATA_PURGE", if (succeeded) "ALL RECORDS ERASED BY SUBJECT." else "ERASE FAILED.")
            updateWidget()
            onComplete(succeeded)
        }
    }

    fun clearActivityLog() {
        viewModelScope.launch {
            auditRepository.clearAllAudits()
            logAudit("MAINTENANCE", "ACTIVITY LOG PURGED BY SUBJECT.")
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }

    fun logAudit(category: String, message: String) {
        viewModelScope.launch {
            auditRepository.log(category, message)
        }
    }

    private fun updateWidget() {
        viewModelScope.launch {
            MandateWidget().updateAll(getApplication<Application>())
        }
    }

    /**
     * Between 23:00 and 05:00, recorded as a neutral fact about when the meal was
     * logged. It is surfaced as a timing label and nothing more — it no longer
     * affects the compliance score or the assessment text.
     */
    private fun isLateNight(timestamp: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
    }

    private fun restoreMessage(failure: Throwable): String =
        when ((failure as? DossierExporter.RestoreException)?.error) {
            is DossierExporter.RestoreError.TooLarge ->
                "That backup file is too large to restore."
            is DossierExporter.RestoreError.UnsupportedVersion ->
                "That backup was written by a newer version of MacroMandate."
            else ->
                "That file could not be read as a MacroMandate backup."
        }

    private companion object {
        const val TAG = "MainViewModel"
        const val DEFAULT_MEAL_NAME = "Untitled meal"
        const val LOCATION_TIMEOUT_SECONDS = 5L

        /** Long edge of the image sent for analysis. See [uriToScaledBase64]. */
        const val ANALYSIS_MAX_EDGE_PX = 800
        const val ANALYSIS_JPEG_QUALITY = 80
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val WRITE_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 60L
        const val CALL_TIMEOUT_SECONDS = 90L

        /**
         * Asks for a bare JSON object. The response is still treated as hostile
         * text — [NutritionSanitizer] does not assume any of this was honoured.
         */
        const val ANALYSIS_PROMPT =
            "Analyze this image of food or drink. Return ONLY a valid JSON object with these keys: " +
                "'foodName' (String), 'calories' (Int), 'proteinGrams' (Float), 'carbsGrams' (Float), " +
                "'fatGrams' (Float), 'isLiquid' (Boolean), 'assessment' (String). " +
                "The 'assessment' field must be one short, factual sentence describing the item. " +
                "Do not include markdown, code blocks, or conversational text. Just raw JSON."
    }

    private fun textRequest(prompt: String) = ChatRequest(
        model = ApiConfig.model,
        messages = listOf(ChatMessage(role = "user", content = listOf(ContentPart.text(prompt))))
    )

    private fun imageRequest(prompt: String, base64Image: String) = ChatRequest(
        model = ApiConfig.model,
        messages = listOf(
            ChatMessage(
                role = "user",
                content = listOf(ContentPart.text(prompt), ContentPart.jpegImage(base64Image))
            )
        )
    )

    private val api: HuggingFaceApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // HEADERS, not BODY: BODY wrote the bearer token and the whole base64
            // image into logcat, where any process with log access could read the
            // user's key. Response bodies are still logged explicitly on failure.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // Vision inference on a cold provider routinely takes 20-40s, which the
            // 10s OkHttp default cut off as a socket timeout — the analysis path
            // failed for reasons that had nothing to do with the image. The write
            // timeout covers uploading a ~200KB base64 payload on a slow link.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // No automatic retry: a retried vision call is a second billable
            // request, and a reply that arrives after a retry has already been
            // issued is how the same meal gets logged twice.
            .retryOnConnectionFailure(false)
            .build()

        Retrofit.Builder()
            .baseUrl(ApiConfig.baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(HuggingFaceApi::class.java)
    }
}
