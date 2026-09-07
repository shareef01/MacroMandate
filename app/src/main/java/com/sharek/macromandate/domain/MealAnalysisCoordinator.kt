package com.sharek.macromandate.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.graphics.scale
import com.google.android.gms.location.LocationServices
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
import java.io.ByteArrayOutputStream
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
            lastKnownLocation()
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

    private suspend fun lastKnownLocation(): android.location.Location? = withContext(Dispatchers.IO) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            Tasks.await(client.lastLocation, LOCATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: SecurityException) {
            null
        } catch (_: TimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun uriToScaledBase64(uri: Uri): String? {
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
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
            Log.w(TAG, "Could not prepare image for analysis", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM preparing image for analysis")
            null
        } finally {
            if (scaled !== decoded) scaled?.recycle()
            decoded?.recycle()
        }
    }
}
