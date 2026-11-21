package com.sharek.macromandate.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
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
import com.sharek.macromandate.network.ApiConfig
import com.sharek.macromandate.network.ChatMessage
import com.sharek.macromandate.network.ChatRequest
import com.sharek.macromandate.network.ContentPart
import com.sharek.macromandate.network.ChatCompletionApi
import com.sharek.macromandate.network.photoAnalysisRequest
import com.sharek.macromandate.network.OpenFoodFactsApi
import com.sharek.macromandate.util.DossierExporter
import com.sharek.macromandate.util.DossierReportGenerator
import com.sharek.macromandate.util.NutritionSanitizer
import com.sharek.macromandate.util.ComplianceEngine
import com.sharek.macromandate.util.EvidenceStore
import com.sharek.macromandate.util.LeniencyVerdict
import com.sharek.macromandate.widget.MandateWidget
import com.sharek.macromandate.util.ImageForensics
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.sharek.macromandate.ui.theme.TerminalTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.PagingData
import androidx.paging.cachedIn
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

enum class ComplianceStatus {
    EXEMPLARY, ACCEPTABLE, SUBVERSIVE, CRISIS, LOCKED
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MealRepository
    private val auditRepository: AuditRepository
    private val preferences: MandatePreferences

    private companion object {
        val mealMutations = Mutex()
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MealRepository(database.mealDao())
        auditRepository = AuditRepository(database.auditDao())

        viewModelScope.launch(Dispatchers.IO) {
            EvidenceStore.reconcileStaging(application)
            mealMutations.withLock { reconcileEvidence() }
        }
        preferences = MandatePreferences(application)
        
        logAudit("SYSTEM_BOOT", "SURVEILLANCE TERMINAL INITIALIZED.")
        
    }

    val searchQuery = MutableStateFlow("")
    val filterType = MutableStateFlow(0)
    val sortOrder = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val mealEntries: Flow<PagingData<MealEntry>> = combine(searchQuery, filterType, sortOrder) { q, f, s ->
        Triple(q, f, s)
    }.flatMapLatest { (q, f, s) ->
        repository.getPagedMeals(q.trim(), f, s)
    }.cachedIn(viewModelScope)
    
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
            initialValue = false
        )

    val locationTrackingEnabled: StateFlow<Boolean> = preferences.locationTrackingEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val shareLocationWithAnalysis = preferences.shareLocationWithAnalysisFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True when analysis can run â€” a key was entered in Settings or baked in at build time. */
    val hasApiKey: StateFlow<Boolean> = preferences.apiKeyFlow
        .map { it.isNotBlank() || ApiConfig.buildTimeKey.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ApiConfig.buildTimeKey.isNotBlank()
        )

    /** Masked for display so the panel can confirm a key exists without revealing it. */
    val apiKeyHint: StateFlow<String> = preferences.apiKeyFlow
        .map { key -> if (key.isBlank()) "" else "â€¢".repeat(8) + key.takeLast(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val terminalTheme: StateFlow<TerminalTheme> = preferences.terminalThemeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TerminalTheme.CYBER_CYAN
        )

    val crtEffectsEnabled: StateFlow<Boolean> = preferences.crtEffectsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun updateTerminalTheme(theme: TerminalTheme) {
        viewModelScope.launch {
            preferences.updateTerminalTheme(theme)
            logAudit("CONFIG", "TERMINAL THEME SET TO ${theme.displayName}.")
        }
    }

    fun updateCrtEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateCrtEffectsEnabled(enabled)
            logAudit("CONFIG", "CRT EFFECTS SET TO $enabled.")
        }
    }

    fun updateApiKey(key: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) { preferences.updateApiKey(key) }
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
            if (saved) logAudit("CONFIG", if (key.isBlank()) "API key cleared." else "API key saved.")
            onResult(saved)
        }
    }

    /** Key entered in Settings wins; the build-time value is only a dev fallback. */
    private suspend fun resolveApiKey(): String =
        preferences.apiKeyFlow.first().ifBlank { ApiConfig.buildTimeKey }

    val complianceScore: StateFlow<Int> = combine(weeklyMeals, calorieTarget) { meals, target ->
        calculateComplianceScore(meals, target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    val complianceStatus: StateFlow<ComplianceStatus> = combine(
        complianceScore,
        weeklyMeals,
        preferences.isPermanentlyLockedFlow
    ) { score, meals, isLocked ->
        if (isLocked) return@combine ComplianceStatus.LOCKED

        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24 * 60 * 60 * 1000L

        var penalty = 0
        if (meals.any { it.isRestricted && it.timestamp >= oneDayAgo }) {
            penalty += 40
        }
        if (meals.any { it.isNightRefueling && it.timestamp >= oneDayAgo }) {
            penalty += 15
        }
        
        val adjustedScore = (score - penalty).coerceAtLeast(0)
        
        ComplianceEngine.statusFor(adjustedScore)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComplianceStatus.EXEMPLARY)

    /**
     * Forbidden sectors are intentionally empty.
     *
     * This previously shipped mock coordinates for central Berlin and Manhattan.
     * A real user eating at either location was flagged for a restricted-zone
     * violation and given a 40-point compliance penalty â€” on its own enough to
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
                
                val tone = when (complianceStatus.value) {
                    ComplianceStatus.SUBVERSIVE -> "EXTREME CORRECTION REQUIRED. AGGRESSIVE TONE."
                    ComplianceStatus.CRISIS -> "TERMINAL WARNING. ABSOLUTE CONDEMNATION."
                    else -> "COLD AND AUTHORITATIVE."
                }
                
                val prompt = "Synthesize these refueling events into a single $tone State Intelligence Briefing. " +
                        "Data: $totals. Items: $mealNames. Judge the subject's biological efficiency and mandate compliance for the day. " +
                        "Return only the briefing text. No conversational filler."

                val response = api.chatCompletion(
                    token = ApiConfig.authHeader(apiKey),
                    request = textRequest(prompt, json = false)
                )

                if (response.isSuccessful) {
                    val briefing = response.body()?.firstMessage().orEmpty().trim()
                    if (briefing.isEmpty()) {
                        _uiState.value = UiState.Error("The provider returned an empty daily summary.")
                        return@launch
                    }
                    _dailyBriefing.value = briefing.take(1_000)
                    logAudit("INTEL_SYNTHESIS", "DAILY BRIEFING GENERATED.")
                    // Only the success path returns to Idle. A `finally` here would
                    // overwrite the Error below before any collector could observe it
                    // â€” StateFlow conflates, and there is no suspension point between.
                    _uiState.value = UiState.Idle
                } else {
                    _uiState.value = UiState.Error("UPLINK FAILURE: ${response.code()}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = UiState.Error("SYNTHESIS ERROR: ${e.localizedMessage?.uppercase()}")
            }
        }
    }

    fun dismissBriefing() {
        _dailyBriefing.value = null
    }

    private val _approvedReset = MutableStateFlow<String?>(null)
    val approvedReset = _approvedReset.asStateFlow()

    fun dismissApprovedReset() {
        _approvedReset.value = null
    }

    fun confirmApprovedReset() {
        val message = _approvedReset.getAndUpdate { null } ?: return
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val cleanup = mealMutations.withLock {
                    withContext(NonCancellable + Dispatchers.IO) {
                        EvidenceStore.markAllForDeletion(getApplication())
                        repository.deleteAllMeals()
                        reconcileEvidence()
                    }
                }
                logAudit("SECURITY_JUDGMENT", "USER CONFIRMED MANDATE RESET.")
                updateWidget()
                _uiState.value = if (cleanup.isSuccess) {
                    UiState.Success("LENIENCY GRANTED: " + message)
                } else {
                    UiState.Error("Meal records cleared. Some photo files could not be removed.")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = UiState.Error("Could not clear meal records.")
            }
        }
    }

    fun submitLeniencyPlea(justification: String) {
        viewModelScope.launch {
            val apiKey = resolveApiKey()
            if (apiKey.isBlank()) {
                _uiState.value = UiState.Error(ApiConfig.NOT_CONFIGURED_MESSAGE)
                return@launch
            }
            _uiState.value = UiState.Loading
            try {
                val prompt = "The subject is pleading for leniency after extreme mandate subversion. " +
                        "Analyze their justification: '$justification'. Cross-reference with their status: ${complianceStatus.value}. " +
                        "Decide if leniency is GRANTED or DENIED. " +
                        "If GRANTED, return exactly: { 'decision': 'GRANTED', 'response': 'State Message' }. " +
                        "If DENIED, return exactly: { 'decision': 'DENIED', 'response': 'Terminal Warning' }. " +
                        "Return raw JSON only."

                val response = api.chatCompletion(
                    token = ApiConfig.authHeader(apiKey),
                    request = textRequest(prompt)
                )

                if (response.isSuccessful) {
                    val responseText = response.body()?.firstMessage().orEmpty()

                    when (val verdict = LeniencyVerdict.parse(responseText)) {
                        is LeniencyVerdict.Granted -> {
                            _approvedReset.value = verdict.message
                            _uiState.value = UiState.Idle
                        }

                        is LeniencyVerdict.Denied -> {
                            logAudit("SECURITY_JUDGMENT", "LENIENCY DENIED. TERMINAL LOCKDOWN.")
                            preferences.setPermanentLockdown(true)
                            _uiState.value = UiState.Error("LENIENCY DENIED: ${verdict.message}")
                        }

                        // Neither branch is reversible, so an unrecognized verdict must
                        // not fall through into lockdown. Report and leave state intact.
                        is LeniencyVerdict.Unparsable -> {
                            _uiState.value = UiState.Error("JUDGMENT ERROR: ${verdict.reason}")
                        }
                    }
                } else {
                    _uiState.value = UiState.Error("JUDGMENT UPLINK FAILURE: ${response.code()}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = UiState.Error("JUDGMENT ERROR: ${e.localizedMessage?.uppercase()}")
            }
        }
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

    fun toggleShareLocationWithAnalysis(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateShareLocationWithAnalysis(enabled)
        }
    }

    /**
     * Clears a permanent lockdown. The verdict that set it comes from a language
     * model, so the subject keeps a way back to their own data.
     */
    fun requestReinstatement() {
        viewModelScope.launch {
            preferences.setPermanentLockdown(false)
            logAudit("SECURITY_JUDGMENT", "REINSTATEMENT PETITION ACCEPTED. LOCKDOWN LIFTED.")
        }
    }

    fun toggleEnforcement(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateEnforcementEnabled(enabled)
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
                    val csv = DossierExporter.generateCsv(repository.getAllMealsAsList())
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray())
                        true
                    } ?: false
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        throw e
                    }
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

    fun exportZipBackupTo(
        context: android.content.Context,
        uri: Uri,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                try {
                    mealMutations.withLock {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            DossierExporter.exportBackupZip(context, repository.getAllMealsAsList(), output)
                            true
                        } ?: false
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        throw e
                    }
                    Log.e("MainViewModel", "ZIP backup export failed", e)
                    false
                }
            }
            if (succeeded) {
                logAudit("DATA_BACKUP", "MEAL RECORDS AND EVIDENCE BACKUP EXPORTED.")
            } else {
                logAudit("DATA_BACKUP", "DATABASE BACKUP EXPORT FAILED.")
            }
            onResult(succeeded)
        }
    }

    fun importZipBackupFrom(
        context: android.content.Context,
        uri: Uri,
        onResult: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            val result: Result<Int> = withContext(Dispatchers.IO) {
                try {
                    mealMutations.withLock {
                        try {
                            val meals = context.contentResolver.openInputStream(uri)?.use { input ->
                                DossierExporter.importBackup(context, input)
                            } ?: error("Failed to read selected backup file.")
                            try {
                                repository.insertMeals(meals)
                            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                                error("The backup contains an ID already present in the meal log. No records were changed.")
                            }
                            Result.success(meals.size)
                        } finally {
                            withContext(NonCancellable) { reconcileEvidence() }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        throw e
                    }
                    Log.w("MainViewModel", "Backup import failed")
                    Result.failure(e)
                }
            }
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                logAudit("DATA_RESTORE", "DATABASE RESTORED FROM ARCHIVE ($count RECORDS).")
                updateWidget()
            } else {
                logAudit("DATA_RESTORE", "DATABASE RESTORE FAILED.")
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
                    if (e is kotlinx.coroutines.CancellationException) {
                        throw e
                    }
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

sealed interface ImageSource {
    val uri: Uri
    val stagingId: String?

    data class LiveCamera(override val uri: Uri, override val stagingId: String) : ImageSource
    data class Imported(override val uri: Uri) : ImageSource {
        override val stagingId: String? = null
    }
}

    class PhotoReview internal constructor(val source: ImageSource, val meal: MealEntry)

    private val _photoReview = MutableStateFlow<PhotoReview?>(null)
    val photoReview = _photoReview.asStateFlow()
    private var analyzingSource: ImageSource? = null
    private val _isSavingPhoto = MutableStateFlow(false)
    val isSavingPhoto = _isSavingPhoto.asStateFlow()

    fun processImageForMacros(source: ImageSource, context: android.content.Context) {
        val activeSource = analyzingSource ?: _photoReview.value?.source
        if (activeSource != null) {
            if (activeSource.uri != source.uri) {
                viewModelScope.launch(Dispatchers.IO) { discardCapture(source) }
            }
            return
        }
        analyzingSource = source
        val uri = source.uri
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val apiKey = resolveApiKey()
                if (apiKey.isBlank()) {
                    _uiState.value = UiState.Error(ApiConfig.NOT_CONFIGURED_MESSAGE)
                    return@launch
                }
                // Local tagging and location sharing are separate choices.
                val location = if (source is ImageSource.LiveCamera && preferences.locationTrackingEnabledFlow.first()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val client = LocationServices.getFusedLocationProviderClient(context)
                            try {
                                // Bounded: an unqualified await blocks this IO thread
                                // indefinitely if Play Services never settles the task.
                                Tasks.await(client.lastLocation, 5, TimeUnit.SECONDS)
                            } catch (_: SecurityException) {
                                null
                            } catch (_: TimeoutException) {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null

                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isNightRefueling = currentHour in 23..23 || currentHour in 0..4

                // One id backs both the meal record and its stored evidence file.
                val mealId = source.stagingId ?: UUID.randomUUID().toString()

                val shareLocation = location != null && preferences.shareLocationWithAnalysisFlow.first()
                val base64Image = withContext(Dispatchers.IO) {
                    val watermarkedUri = if (location != null && shareLocation) {
                        ImageForensics.watermarkImage(
                            context = context,
                            uri = uri,
                            id = mealId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis()
                        )
                    } else null
                    try {
                        uriToScaledBase64(watermarkedUri ?: uri, context)
                    } finally {
                        // The watermarked file is a transient analysis artifact; the
                        // persisted record keeps the original URI. Delete it so the
                        // cache does not grow unboundedly with each capture.
                        watermarkedUri?.path?.let { path -> File(path).delete() }
                    }
                }

                if (base64Image == null) {
                    _uiState.value = UiState.Error("Could not process image")
                    return@launch
                }

                val prompt = "Analyze this image of food or drink. " +
                        "Return ONLY a valid JSON object with the following keys: 'foodName' (String), 'calories' (Int), 'proteinGrams' (Float), 'carbsGrams' (Float), 'fatGrams' (Float), 'isLiquid' (Boolean), 'assessment' (String). " +
                        "The 'assessment' field must be a brief, cold, and authoritative state judgment on the nutritional compliance. " +
                        (if (isNightRefueling) "This is an UNAUTHORIZED NIGHT REFUELING. Mention CIRCADIAN DISCIPLINE BREACH in the assessment." else "") +
                        "Do not include markdown, code blocks, or conversational text. Just raw JSON."
                
                val response = api.chatCompletion(
                    token = ApiConfig.authHeader(apiKey),
                    request = photoAnalysisRequest(
                        ApiConfig.model, prompt, base64Image, ApiConfig.baseUrl.host == "openrouter.ai"
                    )
                )

                if (response.isSuccessful) {
                    val responseText = response.body()?.firstMessage().orEmpty()

                    val jsonStart = responseText.indexOf("{")
                    val jsonEnd = responseText.lastIndexOf("}")
                    
                    if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                        val cleanJson = responseText.substring(jsonStart, jsonEnd + 1)
                        val parsed = try {
                            NutritionSanitizer.parseAndSanitize(cleanJson)
                        } catch (_: IllegalArgumentException) {
                            _uiState.value = UiState.Error("The provider returned no usable nutrition estimate.")
                            return@launch
                        }
                        
                        val isRestricted = if (location != null) checkForbiddenSectors(location.latitude, location.longitude) else false
                        
                        if (isRestricted) {
                            logAudit("SECURITY", "MANDATE VIOLATION: RESTRICTED ZONE INTAKE DETECTED.")
                        }
                        if (isNightRefueling) {
                            logAudit("SECURITY", "MANDATE VIOLATION: CIRCADIAN DISCIPLINE BREACH.")
                        }

                        val newEntry = MealEntry(
                            id = mealId,
                            timestamp = System.currentTimeMillis(),
                            imageUri = null,
                            foodName = parsed.foodName,
                            calories = parsed.calories,
                            proteinGrams = parsed.proteinGrams,
                            carbsGrams = parsed.carbsGrams,
                            fatGrams = parsed.fatGrams,
                            isLiquid = parsed.isLiquid,
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            assessment = parsed.assessment,
                            isRestricted = isRestricted,
                            isNightRefueling = isNightRefueling
                        )

                        _photoReview.value = PhotoReview(source, newEntry)
                        _uiState.value = UiState.Idle
                    } else {
                        val choices = response.body()?.choices.orEmpty()
                        val finish = when (choices.firstOrNull()?.finishReason) {
                            "length" -> "length"
                            "stop" -> "stop"
                            "content_filter" -> "content_filter"
                            "error" -> "error"
                            else -> "unknown"
                        }
                        Log.w("MainViewModel", "No nutrition JSON: choices=${choices.size}, finish=$finish, chars=${responseText.length}")
                        val errorMsg = if (finish == "length") {
                            "The provider stopped before returning an estimate. Try the photo again."
                        } else {
                            "The provider returned no usable nutrition estimate."
                        }
                        _uiState.value = UiState.Error(errorMsg)
                    }
                } else {
                    response.errorBody()?.close()
                    val errorMsg = when (response.code()) {
                        401, 403 -> "Check your ${ApiConfig.providerName} API key and permissions in Settings."
                        402 -> "${ApiConfig.providerName} rejected the request because of account credit or spending limits."
                        429 -> "${ApiConfig.providerName} is rate limiting requests. Try again later."
                        else -> "Photo analysis failed (HTTP ${response.code()})."
                    }
                    _uiState.value = UiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("MainViewModel", "Photo analysis or meal save failed")
                _uiState.value = UiState.Error("Could not analyze or save the photo meal.")
            } finally {
                analyzingSource = null
                if (_photoReview.value?.source !== source) {
                    withContext(NonCancellable + Dispatchers.IO) { discardCapture(source) }
                }
            }
        }
    }

    fun discardPhotoReview() {
        if (_isSavingPhoto.value) return
        val review = _photoReview.getAndUpdate { null } ?: return
        viewModelScope.launch(Dispatchers.IO) { discardCapture(review.source) }
    }

    fun savePhotoReview(
        name: String, calories: Int, protein: Float, carbs: Float, fat: Float,
        isLiquid: Boolean, onResult: (Boolean) -> Unit
    ) {
        val review = _photoReview.value ?: return
        if (!_isSavingPhoto.compareAndSet(false, true)) return
        val edited = review.meal.copy(
            foodName = name.trim().take(120), calories = calories.coerceIn(0, 20_000),
            proteinGrams = protein.coerceIn(0f, 1_000f), carbsGrams = carbs.coerceIn(0f, 1_000f),
            fatGrams = fat.coerceIn(0f, 1_000f), isLiquid = isLiquid
        )
        viewModelScope.launch {
            try {
                mealMutations.withLock {
                    withContext(Dispatchers.IO) {
                        try {
                            val stored = EvidenceStore.commit(getApplication(), review.source.uri)
                                ?: error("Could not store the meal photo.")
                            repository.insertMeal(edited.copy(imageUri = stored.toString()))
                        } catch (e: Exception) {
                            withContext(NonCancellable) { reconcileEvidence() }
                            throw e
                        }
                    }
                }
                _photoReview.value = null
                withContext(NonCancellable + Dispatchers.IO) { discardCapture(review.source) }
                logAudit("DATA_INGEST", "PHOTO MEAL LOGGED.")
                updateWidget()
                _uiState.value = UiState.Success(edited.foodName)
                onResult(true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("MainViewModel", "Photo meal save failed")
                onResult(false)
            } finally {
                _isSavingPhoto.value = false
            }
        }
    }

    private fun discardCapture(source: ImageSource) {
        if (source is ImageSource.LiveCamera) {
            try {
                EvidenceStore.discardStaging(getApplication(), source.uri)
            } catch (e: SecurityException) {
                Log.w("MainViewModel", "Staged photo cleanup deferred", e)
            } finally {
                EvidenceStore.releaseStaging(source.uri)
            }
        }
    }

    override fun onCleared() {
        (_photoReview.value?.source as? ImageSource.LiveCamera)?.let {
            EvidenceStore.releaseStaging(it.uri)
        }
        super.onCleared()
    }

    private fun uriToScaledBase64(uri: Uri, context: android.content.Context): String? =
        ImageForensics.encodeAnalysisJpeg(context, uri)?.let { jpeg ->
            Base64.encodeToString(jpeg, Base64.NO_WRAP)
        }

    fun addMealEntry(entry: MealEntry) {
        viewModelScope.launch {
            if (saveMeal(entry)) updateWidget()
        }
    }

    fun observeMeal(id: String) = repository.observeMeal(id)

    fun deleteMealEntry(id: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val cleanup = try {
                mealMutations.withLock {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val photo = repository.getMealById(id)?.imageUri
                        EvidenceStore.markForDeletion(getApplication(), photo)
                        repository.deleteMeal(id)
                        reconcileEvidence()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = UiState.Error("Failed to delete the meal record.")
                return@launch
            }
            logAudit("DATA_PURGE", "RECORD EXPUNGED.")
            updateWidget()
            if (cleanup.isFailure) _uiState.value = UiState.Error("Meal deleted. Its photo file could not be removed.")
            onDeleted()
        }
    }

    private suspend fun reconcileEvidence(): Result<Unit> {
        return try {
            val referenced = repository.getPhotoReferences().toSet()
            EvidenceStore.reconcileDurable(getApplication(), referenced)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MainViewModel", "Evidence reconciliation deferred")
            Result.failure(e)
        }
    }

    fun updateMealEntry(updatedMeal: MealEntry) {
        viewModelScope.launch {
            try {
                mealMutations.withLock { repository.updateMeal(updatedMeal) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = UiState.Error("Failed to update the meal record.")
                return@launch
            }
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
        isLiquid: Boolean,
        onSaved: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val mealId = UUID.randomUUID().toString()
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isNightRefueling = currentHour in 23..23 || currentHour in 0..4

            val entry = MealEntry(
                id = mealId,
                timestamp = System.currentTimeMillis(),
                imageUri = null,
                foodName = foodName.ifBlank { "MANUAL REFUELING" },
                calories = calories.coerceAtLeast(0),
                proteinGrams = protein.coerceAtLeast(0f),
                carbsGrams = carbs.coerceAtLeast(0f),
                fatGrams = fat.coerceAtLeast(0f),
                isLiquid = isLiquid,
                latitude = null,
                longitude = null,
                assessment = if (isNightRefueling) "MANUAL LOG: CIRCADIAN DISCIPLINE BREACH." else "MANUAL TELEMETRY LOGGED.",
                isRestricted = false,
                isNightRefueling = isNightRefueling
            )

            if (!saveMeal(entry)) {
                onSaved(false)
                return@launch
            }
            onSaved(true)
            logAudit("DATA_INGEST", "MANUAL RECORD LOGGED: ${entry.foodName.uppercase()}")
            updateWidget()
            _uiState.value = UiState.Success(entry.foodName)
        }
    }

    private suspend fun saveMeal(entry: MealEntry): Boolean {
        return try {
            mealMutations.withLock { repository.insertMeal(entry) }
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.value = UiState.Error("Failed to save the meal record.")
            false
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

    private fun textRequest(prompt: String, json: Boolean = true) = ChatRequest(
        model = ApiConfig.model,
        messages = listOf(ChatMessage(role = "user", content = listOf(ContentPart.text(prompt)))),
        responseFormat = if (json) mapOf("type" to "json_object") else null
    )

    private val api: ChatCompletionApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // HEADERS, not BODY: BODY wrote the bearer token and the whole base64
            // image into logcat, where any process with log access could read the
            // user's key. Response bodies are still logged explicitly on failure.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
        val retryInterceptor = okhttp3.Interceptor { chain ->
            var response = chain.proceed(chain.request())
            var tryCount = 0
            val maxRetries = 2
            while (!response.isSuccessful && (response.code == 503 || response.code == 429) && tryCount < maxRetries) {
                tryCount++
                response.close()
                try {
                    Thread.sleep(1200L * tryCount)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("Analysis retry interrupted").apply { initCause(e) }
                }
                if (chain.call().isCanceled()) throw java.io.IOException("Analysis canceled")
                response = chain.proceed(chain.request())
            }
            response
        }
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(retryInterceptor)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val body = response.body
                val limit = 128L * 1024
                try {
                    if (body.contentLength() > limit || body.source().request(limit + 1)) {
                        throw java.io.IOException("Analysis response exceeds the size limit.")
                    }
                    response
                } catch (e: Exception) {
                    response.close()
                    throw e
                }
            }
            .build()

        Retrofit.Builder()
            .baseUrl(ApiConfig.baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ChatCompletionApi::class.java)
    }

    private val offApi: OpenFoodFactsApi by lazy {
        val client = OkHttpClient()
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(OpenFoodFactsApi::class.java)
    }

    private val _barcodeResult = MutableStateFlow<MealEntry?>(null)
    val barcodeResult: StateFlow<MealEntry?> = _barcodeResult

    fun clearBarcodeResult() {
        _barcodeResult.value = null
    }

    fun lookupBarcode(barcode: String, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val response = offApi.getProduct(barcode)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.status == 1 && body.product != null) {
                        val product = body.product
                        val n = product.nutriments
                        
                        val kcal = n?.caloriesServing ?: n?.calories100g ?: 0.0
                        val p = n?.proteinServing ?: n?.protein100g ?: 0.0
                        val c = n?.carbsServing ?: n?.carbs100g ?: 0.0
                        val f = n?.fatServing ?: n?.fat100g ?: 0.0
                        
                        val entry = MealEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            foodName = product.productName ?: "Scanned Product",
                            calories = kcal.toInt(),
                            proteinGrams = p.toFloat(),
                            carbsGrams = c.toFloat(),
                            fatGrams = f.toFloat(),
                            imageUri = null,
                            isLiquid = false,
                            assessment = "Barcode Match",
                            isRestricted = false
                        )
                        _barcodeResult.value = entry
                        _uiState.value = UiState.Success(entry.foodName)
                    } else {
                        _uiState.value = UiState.Error("Product not found in OpenFoodFacts.")
                    }
                } else {
                    _uiState.value = UiState.Error("Failed to lookup barcode.")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = UiState.Error("Barcode lookup failed.")
            }
        }
    }

}
