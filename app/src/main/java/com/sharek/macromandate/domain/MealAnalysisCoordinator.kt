package com.sharek.macromandate.domain

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.network.AnalysisError
import com.sharek.macromandate.network.AnalysisFailure
import com.sharek.macromandate.network.NutritionAnalyzer
import com.sharek.macromandate.util.EvidenceStore
import com.sharek.macromandate.util.ImageForensics
import com.sharek.macromandate.viewmodel.PendingAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MealAnalysisCoordinator(
    private val context: Context,
    private val analyzer: NutritionAnalyzer,
    private val preferences: MandatePreferences
) {
    private companion object {
        const val TAG = "MealAnalysisCoordinator"
        const val LOCATION_TIMEOUT_SECONDS = 5L
        const val ANALYSIS_MAX_EDGE_PX = 800
        const val ANALYSIS_JPEG_QUALITY = 80
        const val MAX_LOCATION_AGE_MS = 2L * 60 * 1000
        const val MAX_LOCATION_ACCURACY_METERS = 500f
    }

    suspend fun analyzePhoto(
        uri: Uri,
        apiKey: String
    ): Result<PendingAnalysis> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(AnalysisFailure(AnalysisError.NoApiKey))
        }

        val capturedAt = System.currentTimeMillis()

        // Read location if local tracking is enabled
        val isLocationEnabled = preferences.locationTrackingEnabledFlow.first()
        val location = if (isLocationEnabled) {
            currentLocation()
        } else {
            null
        }

        // Only watermark coordinates onto the uploaded image if the user explicitly
        // enabled "Include location in AI analysis image"
        val includeInAi = preferences.includeLocationInAiFlow.first()

        val watermarkedUri = if (location != null && includeInAi) {
            ImageForensics.watermarkImage(
                context = context,
                uri = uri,
                id = UUID.randomUUID().toString(),
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = capturedAt
            )
        } else null

        val base64Image = try {
            uriToScaledBase64(watermarkedUri ?: uri)
        } finally {
            watermarkedUri?.path?.let { path -> File(path).delete() }
        }

        if (base64Image == null) {
            return@withContext Result.failure(AnalysisFailure(AnalysisError.ImageUnreadable))
        }

        analyzer.analyze(apiKey, base64Image).map { nutrition ->
            PendingAnalysis(
                sourceImage = uri,
                nutrition = nutrition,
                capturedAt = capturedAt,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
        }
    }

    suspend fun releaseCapture(uri: Uri) = withContext(Dispatchers.IO) {
        EvidenceStore.delete(context, uri.toString())
    }

    private suspend fun currentLocation(): android.location.Location? = withContext(Dispatchers.IO) {
        val cancellation = CancellationTokenSource()
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val location = Tasks.await(
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token),
                LOCATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
            location?.takeIf {
                System.currentTimeMillis() - it.time <= MAX_LOCATION_AGE_MS &&
                    it.hasAccuracy() && it.accuracy <= MAX_LOCATION_ACCURACY_METERS
            }
        } catch (_: SecurityException) {
            null
        } catch (_: TimeoutException) {
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun uriToScaledBase64(uri: Uri): String? {
        return try {
            ImageForensics.encodeAnalysisJpeg(
                context, uri, ANALYSIS_MAX_EDGE_PX, ANALYSIS_JPEG_QUALITY
            )?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare image for analysis", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM preparing image for analysis")
            null
        }
    }
}
