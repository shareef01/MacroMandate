package com.sharek.macromandate.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharek.macromandate.BuildConfig
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.network.HuggingFaceApi
import com.sharek.macromandate.network.HuggingFaceRequest
import com.sharek.macromandate.util.DossierExporter
import com.sharek.macromandate.widget.MandateWidget
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
    EXEMPLARY, ACCEPTABLE, SUBVERSIVE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MealRepository
    private val preferences: MandatePreferences

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MealRepository(database.mealDao())
        preferences = MandatePreferences(application)
    }

    val mealEntries: StateFlow<List<MealEntry>> = repository.getAllMeals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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

    val complianceStatus: StateFlow<ComplianceStatus> = complianceScore.map { score ->
        when {
            score >= 90 -> ComplianceStatus.EXEMPLARY
            score >= 70 -> ComplianceStatus.ACCEPTABLE
            else -> ComplianceStatus.SUBVERSIVE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComplianceStatus.EXEMPLARY)

    private fun calculateComplianceScore(meals: List<MealEntry>, dailyTarget: Int): Int {
        if (meals.isEmpty()) return 100

        val calendar = Calendar.getInstance()
        val totalsByDay = meals.groupBy {
            val entryCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            entryCal.get(Calendar.DAY_OF_YEAR)
        }.mapValues { it.value.sumOf { meal -> meal.calories } }

        var totalDeviation = 0f
        val daysEvaluated = totalsByDay.size.coerceAtLeast(1)

        totalsByDay.values.forEach { dailyTotal ->
            val deviation = abs(dailyTotal - dailyTarget).toFloat() / dailyTarget
            totalDeviation += deviation
        }

        val averageDeviationPercent = (totalDeviation / daysEvaluated) * 100
        return (100 - averageDeviationPercent.toInt()).coerceIn(0, 100)
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updateCalorieTarget(target: Int) {
        viewModelScope.launch {
            preferences.updateCalorieTarget(target)
        }
    }

    fun toggleEnforcement(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateEnforcementEnabled(enabled)
        }
    }

    fun exportData(onCsvReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = DossierExporter.generateCsv(mealEntries.value)
            onCsvReady(csv)
        }
    }

    fun processImageForMacros(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val base64Image = withContext(Dispatchers.IO) {
                    uriToScaledBase64(uri, context)
                }

                if (base64Image == null) {
                    _uiState.value = UiState.Error("Could not process image")
                    return@launch
                }

                val prompt = "Analyze this image of food or drink. Return ONLY a valid JSON object with the following keys: 'foodName' (String), 'calories' (Int), 'proteinGrams' (Float), 'carbsGrams' (Float), 'fatGrams' (Float), 'isLiquid' (Boolean). Do not include any markdown formatting, code blocks, or conversational text. Just the raw JSON."
                
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
                        
                        val newEntry = MealEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            imageUri = uri.toString(),
                            foodName = jsonObject.getString("foodName"),
                            calories = jsonObject.getInt("calories"),
                            proteinGrams = jsonObject.optDouble("proteinGrams", 0.0).toFloat(),
                            carbsGrams = jsonObject.optDouble("carbsGrams", 0.0).toFloat(),
                            fatGrams = jsonObject.optDouble("fatGrams", 0.0).toFloat(),
                            isLiquid = jsonObject.optBoolean("isLiquid", false)
                        )

                        repository.insertMeal(newEntry)
                        _uiState.value = UiState.Success(newEntry.foodName)
                    } else {
                        Log.e("MainViewModel", "No JSON found in response: $responseText")
                        _uiState.value = UiState.Error("AI response was not in the expected format")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("MainViewModel", "API Error: $errorBody")
                    _uiState.value = UiState.Error("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error processing image", e)
                _uiState.value = UiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    private fun uriToScaledBase64(uri: Uri, context: android.content.Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            
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
        }
    }

    fun deleteMealEntry(id: String) {
        viewModelScope.launch {
            repository.deleteMeal(id)
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }

    private val api: HuggingFaceApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
