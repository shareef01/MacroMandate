package com.sharek.macromandate.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.sharek.macromandate.service.MandateSurveillanceService
import com.sharek.macromandate.network.HuggingFaceApi
import com.sharek.macromandate.network.HuggingFaceRequest
import com.sharek.macromandate.util.DossierExporter
import com.sharek.macromandate.util.ComplianceEngine
import com.sharek.macromandate.widget.MandateWidget
import com.sharek.macromandate.util.ImageForensics
import com.sharek.macromandate.worker.EnforcementScheduler
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.UUID
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

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MealRepository(database.mealDao())
        auditRepository = AuditRepository(database.auditDao())
        preferences = MandatePreferences(application)
        
        logAudit("SYSTEM_BOOT", "SURVEILLANCE TERMINAL INITIALIZED.")
        
        // Activate Omnipresent Surveillance
        val serviceIntent = android.content.Intent(application, MandateSurveillanceService::class.java)
        application.startForegroundService(serviceIntent)
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

    val complianceScore: StateFlow<Int> = combine(weeklyMeals, calorieTarget) { meals, target ->
        calculateComplianceScore(meals, target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    val complianceStatus: StateFlow<ComplianceStatus> = combine(
        complianceScore,
        mealEntries,
        preferences.isPermanentlyLockedFlow
    ) { score, meals, isLocked ->
        if (isLocked) return@combine ComplianceStatus.LOCKED

        var penalty = 0
        if (meals.any { it.isRestricted && (System.currentTimeMillis() - it.timestamp) < 24 * 60 * 60 * 1000 }) {
            penalty += 40
        }
        if (meals.any { it.isNightRefueling && (System.currentTimeMillis() - it.timestamp) < 24 * 60 * 60 * 1000 }) {
            penalty += 15
        }
        
        val adjustedScore = (score - penalty).coerceAtLeast(0)
        
        ComplianceEngine.statusFor(adjustedScore)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComplianceStatus.EXEMPLARY)

    private fun checkForbiddenSectors(lat: Double, lng: Double): Boolean {
        // Mock Forbidden Sectors: Burger Corridor, Donut District
        val forbiddenPoints = listOf(
            Pair(52.5200, 13.4050), // Sector A
            Pair(40.7128, -74.0060)  // Sector B
        )
        
        return forbiddenPoints.any { (fLat, fLng) ->
            abs(lat - fLat) < 0.005 && abs(lng - fLng) < 0.005 // Approx 500m
        }
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

                val response = api.analyzeImage(
                    token = "Bearer ${BuildConfig.HUGGINGFACE_API_KEY}",
                    request = HuggingFaceRequest(inputs = "User: $prompt\nAssistant:")
                )

                if (response.isSuccessful) {
                    val responseText = response.body()?.firstOrNull()?.generatedText ?: ""
                    _dailyBriefing.value = responseText.substringAfter("Assistant:").trim()
                    logAudit("INTEL_SYNTHESIS", "DAILY BRIEFING GENERATED.")
                } else {
                    _uiState.value = UiState.Error("UPLINK FAILURE: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("SYNTHESIS ERROR: ${e.localizedMessage?.uppercase()}")
            } finally {
                _uiState.value = UiState.Idle
            }
        }
    }

    fun dismissBriefing() {
        _dailyBriefing.value = null
    }

    fun submitLeniencyPlea(justification: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val prompt = "The subject is pleading for leniency after extreme mandate subversion. " +
                        "Analyze their justification: '$justification'. Cross-reference with their status: ${complianceStatus.value}. " +
                        "Decide if leniency is GRANTED or DENIED. " +
                        "If GRANTED, return exactly: { 'decision': 'GRANTED', 'response': 'State Message' }. " +
                        "If DENIED, return exactly: { 'decision': 'DENIED', 'response': 'Terminal Warning' }. " +
                        "Return raw JSON only."

                val response = api.analyzeImage(
                    token = "Bearer ${BuildConfig.HUGGINGFACE_API_KEY}",
                    request = HuggingFaceRequest(inputs = "User: $prompt\nAssistant:")
                )

                if (response.isSuccessful) {
                    val responseText = response.body()?.firstOrNull()?.generatedText ?: ""
                    val jsonStart = responseText.indexOf("{")
                    val jsonEnd = responseText.lastIndexOf("}")
                    
                    if (jsonStart != -1 && jsonEnd != -1) {
                        val json = JSONObject(responseText.substring(jsonStart, jsonEnd + 1))
                        val decision = json.getString("decision")
                        val msg = json.getString("response")
                        
                        if (decision == "GRANTED") {
                            logAudit("SECURITY_JUDGMENT", "LENIENCY GRANTED. MANDATE RESET.")
                            repository.deleteAllMeals() // Wipe the shame
                            _uiState.value = UiState.Success("LENIENCY GRANTED: $msg")
                        } else {
                            logAudit("SECURITY_JUDGMENT", "LENIENCY DENIED. TERMINAL LOCKDOWN.")
                            preferences.setPermanentLockdown(true)
                            _uiState.value = UiState.Error("LENIENCY DENIED: $msg")
                        }
                    } else {
                        _uiState.value = UiState.Error("JUDGMENT ERROR: UNPARSABLE VERDICT.")
                    }
                } else {
                    _uiState.value = UiState.Error("JUDGMENT UPLINK FAILURE: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("JUDGMENT ERROR: ${e.localizedMessage?.uppercase()}")
            } finally {
                _uiState.value = UiState.Idle
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

    fun exportData(onCsvReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = DossierExporter.generateCsv(mealEntries.value)
            logAudit("DATA_EXPORT", "DOSSIER EXFILTRATED.")
            onCsvReady(csv)
        }
    }

    fun processImageForMacros(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Tactical Location Acquisition
                val location = withContext(Dispatchers.IO) {
                    try {
                        val client = LocationServices.getFusedLocationProviderClient(context)
                        try {
                            Tasks.await(client.lastLocation)
                        } catch (_: SecurityException) {
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isNightRefueling = currentHour in 23..23 || currentHour in 0..4
                
                val base64Image = withContext(Dispatchers.IO) {
                    val watermarkedUri = if (location != null) {
                        ImageForensics.watermarkImage(
                            context = context,
                            uri = uri,
                            id = UUID.randomUUID().toString(),
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
                
                val fullInputs = "User: $prompt <image> data:image/jpeg;base64,$base64Image\nAssistant:"
                
                val response = api.analyzeImage(
                    token = "Bearer ${BuildConfig.HUGGINGFACE_API_KEY}",
                    request = HuggingFaceRequest(inputs = fullInputs)
                )

                if (response.isSuccessful) {
                    val responseList = response.body()
                    val responseText = responseList?.firstOrNull()?.generatedText ?: ""
                    
                    val jsonStart = responseText.indexOf("{")
                    val jsonEnd = responseText.lastIndexOf("}")
                    
                    if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                        val cleanJson = responseText.substring(jsonStart, jsonEnd + 1)
                        val jsonObject = JSONObject(cleanJson)
                        
                        val isRestricted = if (location != null) checkForbiddenSectors(location.latitude, location.longitude) else false
                        
                        if (isRestricted) {
                            logAudit("SECURITY", "MANDATE VIOLATION: RESTRICTED ZONE INTAKE DETECTED.")
                        }
                        if (isNightRefueling) {
                            logAudit("SECURITY", "MANDATE VIOLATION: CIRCADIAN DISCIPLINE BREACH.")
                        }

                        val newEntry = MealEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            imageUri = uri.toString(),
                            foodName = jsonObject.getString("foodName"),
                            calories = jsonObject.getInt("calories"),
                            proteinGrams = jsonObject.optDouble("proteinGrams", 0.0).toFloat(),
                            carbsGrams = jsonObject.optDouble("carbsGrams", 0.0).toFloat(),
                            fatGrams = jsonObject.optDouble("fatGrams", 0.0).toFloat(),
                            isLiquid = jsonObject.optBoolean("isLiquid", false),
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            assessment = jsonObject.optString("assessment", "NO ASSESSMENT PROVIDED."),
                            isRestricted = isRestricted,
                            isNightRefueling = isNightRefueling
                        )

                        repository.insertMeal(newEntry)
                        logAudit("DATA_INGEST", "RECORD LOGGED: ${newEntry.foodName.uppercase()}")
                        updateWidget()
                        _uiState.value = UiState.Success(newEntry.foodName)
                    } else {
                        Log.e("MainViewModel", "No JSON found in response: $responseText")
                        val errorMsg = if (complianceStatus.value == ComplianceStatus.SUBVERSIVE) "[ MANDATE VIOLATION ] INVALID AI DATA" else "AI response was not in the expected format"
                        _uiState.value = UiState.Error(errorMsg)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("MainViewModel", "API Error: $errorBody")
                    val errorMsg = if (complianceStatus.value == ComplianceStatus.SUBVERSIVE) "[ MANDATE VIOLATION ] SERVER EMBARGO" else "Server error: ${response.code()}"
                    _uiState.value = UiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error processing image", e)
                val errorMsg = if (complianceStatus.value == ComplianceStatus.SUBVERSIVE) "[ MANDATE VIOLATION ] ${e.localizedMessage?.uppercase()}" else (e.localizedMessage ?: "Unknown error")
                _uiState.value = UiState.Error(errorMsg)
            }
        }
    }

    private fun uriToScaledBase64(uri: Uri, context: android.content.Context): String? {
        return try {
            // Decode bounds first so a full-resolution camera frame is never materialized
            // (avoids OutOfMemoryError on large images).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            val originalBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inSampleSize = ImageForensics.calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension = 1600)
                })
            } ?: return null

            val scale = 800f / Math.max(originalBitmap.width, originalBitmap.height).coerceAtLeast(1)
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (originalBitmap.width * scale).toInt(),
                    (originalBitmap.height * scale).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error converting URI to Base64", e)
            null
        }
    }

    fun addMealEntry(entry: MealEntry) {
        viewModelScope.launch {
            repository.insertMeal(entry)
            updateWidget()
        }
    }

    fun deleteMealEntry(id: String) {
        viewModelScope.launch {
            repository.deleteMeal(id)
            logAudit("DATA_PURGE", "RECORD REMOVED: $id")
            updateWidget()
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

    private val api: HuggingFaceApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // Never log request/response bodies (which contain the auth token and image data) in release builds.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(HuggingFaceApi::class.java)
    }
}
