package com.sharek.macromandate.network

import com.sharek.macromandate.util.NutritionSanitizer
import com.sharek.macromandate.util.ParsedNutrition
import kotlinx.coroutines.CancellationException

/**
 * Turns an encoded image into a nutrition estimate, or an [AnalysisError].
 *
 * This is the part of the capture flow that talks to the provider and reads what
 * comes back, split out of `MainViewModel` for one reason: **it could not be
 * tested there**. The ViewModel built its own Retrofit instance in a `by lazy`
 * and reached for `Application` directly, so the most hostile input surface in
 * the application — a third-party model's free-form reply — had no unit coverage
 * at all. Every case was reasoned about and none was executed.
 *
 * Nothing Android-specific belongs here. Bitmap decoding, EXIF, location and
 * storage stay in the ViewModel where the `Context` is; this takes a string and
 * returns a value, which is what makes it checkable.
 */
class NutritionAnalyzer(
    private val api: HuggingFaceApi,
    private val modelId: String,
    private val promptBuilder: () -> String,
    /** Receives detail that must never reach the UI. No-op in release. */
    private val debugLog: (String) -> Unit = {}
) {

    /**
     * Runs one analysis.
     *
     * @param apiKey the caller's credential; blank is rejected without a request
     *   rather than spending one to be told 401.
     * @param base64Jpeg the already-downsampled, upright frame.
     */
    suspend fun analyze(apiKey: String, base64Jpeg: String): Result<ParsedNutrition> {
        if (apiKey.isBlank()) return failure(AnalysisError.NoApiKey)
        if (base64Jpeg.isBlank()) return failure(AnalysisError.ImageUnreadable)

        val response = try {
            api.chatCompletion(
                token = ApiConfig.authHeader(apiKey),
                request = imageRequest(promptBuilder(), base64Jpeg)
            )
        } catch (e: CancellationException) {
            // Cancellation is the user leaving, not a failure to report.
            throw e
        } catch (e: Exception) {
            debugLog("Analysis request failed: $e")
            return failure(AnalysisError.fromThrowable(e))
        }

        if (!response.isSuccessful) {
            // The body can echo the request or carry a provider HTML page, so it
            // is never shown to the user and never logged in a release build.
            debugLog("Provider returned ${response.code()}: ${runCatching { response.errorBody()?.string() }.getOrNull()}")
            return failure(AnalysisError.fromHttpStatus(response.code()))
        }

        val replyText = response.body()?.firstMessage().orEmpty()
        return readNutrition(replyText)
    }

    /**
     * Extracts one nutrition object from a free-form reply.
     *
     * Takes the span between the first `{` and the last `}`. That is deliberately
     * permissive: providers wrap the object in prose, in markdown fences, or in
     * both, and a stricter parse would reject replies that plainly contain the
     * answer. It also means a reply with two objects yields the outermost span,
     * which [NutritionSanitizer] then either reads or rejects — no partial
     * result is ever constructed.
     */
    internal fun readNutrition(replyText: String): Result<ParsedNutrition> {
        val start = replyText.indexOf('{')
        val end = replyText.lastIndexOf('}')
        if (start == -1 || end <= start) {
            debugLog("No JSON object in reply: $replyText")
            return failure(AnalysisError.UnreadableResult)
        }

        return try {
            Result.success(NutritionSanitizer.parseAndSanitize(replyText.substring(start, end + 1)))
        } catch (e: Exception) {
            debugLog("Unparsable nutrition object: $e")
            failure(AnalysisError.UnreadableResult)
        }
    }

    private fun imageRequest(prompt: String, base64Jpeg: String) = ChatRequest(
        model = modelId,
        messages = listOf(
            ChatMessage(
                role = "user",
                content = listOf(ContentPart.text(prompt), ContentPart.jpegImage(base64Jpeg))
            )
        )
    )

    private fun failure(error: AnalysisError): Result<ParsedNutrition> =
        Result.failure(AnalysisFailure(error))
}

/**
 * Carries an [AnalysisError] through a [Result].
 *
 * The message is the case name, not the user-facing copy: that lives in a string
 * resource and is resolved at display time. An exception message here would only
 * end up in a log twice.
 */
class AnalysisFailure(val error: AnalysisError) : Exception(error::class.simpleName)

/** The [AnalysisError] behind a failed analysis, or [AnalysisError.Unknown]. */
val Throwable.analysisError: AnalysisError
    get() = (this as? AnalysisFailure)?.error ?: AnalysisError.Unknown
