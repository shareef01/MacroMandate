package com.sharek.macromandate.network

import com.sharek.macromandate.util.NutritionBounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The analysis round trip, end to end, against a fake provider.
 *
 * This is the coverage the extraction was for. While this logic lived inline in
 * `MainViewModel` — behind a `by lazy` Retrofit instance and an `Application`
 * reference — none of it could be executed by a unit test, so the app's most
 * hostile input surface was reasoned about but never run.
 */
class NutritionAnalyzerTest {

    // ---- fake provider ------------------------------------------------------

    private class FakeApi(
        private val handler: () -> Response<ChatResponse>
    ) : HuggingFaceApi {
        var lastRequest: ChatRequest? = null
        var callCount = 0

        override suspend fun chatCompletion(token: String, request: ChatRequest): Response<ChatResponse> {
            callCount++
            lastRequest = request
            lastToken = token
            return handler()
        }

        var lastToken: String? = null
    }

    private fun reply(content: String): Response<ChatResponse> =
        Response.success(ChatResponse(listOf(ChatChoice(ChatResponseMessage(content)))))

    private fun httpError(code: Int, body: String = "{}"): Response<ChatResponse> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))

    private fun analyzerFor(api: HuggingFaceApi) = NutritionAnalyzer(
        api = api,
        modelId = "test/model",
        promptBuilder = { "prompt" }
    )

    private fun analyze(response: () -> Response<ChatResponse>) = runBlocking {
        analyzerFor(FakeApi(response)).analyze("hf_test", "BASE64DATA")
    }

    // ---- the happy path -----------------------------------------------------

    @Test
    fun aWellFormedReplyProducesANutritionEstimate() {
        val result = analyze {
            reply("""{"foodName":"Porridge","calories":350,"proteinGrams":12.5,"carbsGrams":60,"fatGrams":6}""")
        }
        val nutrition = result.getOrThrow()
        assertEquals("Porridge", nutrition.foodName)
        assertEquals(350, nutrition.calories)
        assertEquals(12.5f, nutrition.proteinGrams, 0.01f)
    }

    @Test
    fun theRequestCarriesTheModelThePromptAndTheImage() {
        val api = FakeApi { reply("""{"foodName":"X"}""") }
        runBlocking { analyzerFor(api).analyze("hf_abc", "IMAGEDATA") }

        val request = api.lastRequest!!
        assertEquals("test/model", request.model)
        assertEquals("Bearer hf_abc", api.lastToken)

        val parts = request.messages.single().content
        assertEquals("prompt", parts.first { it.type == "text" }.text)
        val image = parts.first { it.type == "image_url" }.imageUrl!!.url
        assertTrue("image must be sent as a data URI", image.startsWith("data:image/jpeg;base64,"))
        assertTrue(image.endsWith("IMAGEDATA"))
    }

    // ---- credentials --------------------------------------------------------

    @Test
    fun aBlankKeyIsRejectedWithoutSpendingARequest() {
        val api = FakeApi { reply("{}") }
        val result = runBlocking { analyzerFor(api).analyze("   ", "DATA") }

        assertEquals(AnalysisError.NoApiKey, result.exceptionOrNull()!!.analysisError)
        assertEquals("no request should have been made", 0, api.callCount)
    }

    @Test
    fun aBlankImageIsRejectedWithoutSpendingARequest() {
        val api = FakeApi { reply("{}") }
        val result = runBlocking { analyzerFor(api).analyze("hf_test", "") }

        assertEquals(AnalysisError.ImageUnreadable, result.exceptionOrNull()!!.analysisError)
        assertEquals(0, api.callCount)
    }

    // ---- HTTP failures ------------------------------------------------------

    @Test
    fun anExpiredKeyPointsAtSettingsRatherThanTheNetwork() {
        val result = analyze { httpError(401) }
        assertEquals(AnalysisError.CredentialRejected, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun rateLimitingIsReportedAsSuch() {
        val result = analyze { httpError(429) }
        assertEquals(AnalysisError.RateLimited, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun providerOutagesCarryTheStatusForLogsOnly() {
        val result = analyze { httpError(503) }
        val error = result.exceptionOrNull()!!.analysisError
        assertEquals(AnalysisError.ProviderUnavailable(503), error)
    }

    @Test
    fun anHtmlErrorPageDoesNotCrashTheParser() {
        // Gateways return HTML with a 200 more often than anyone would like.
        val result = analyze { reply("<html><body>502 Bad Gateway</body></html>") }
        assertEquals(AnalysisError.UnreadableResult, result.exceptionOrNull()!!.analysisError)
    }

    // ---- transport failures -------------------------------------------------

    @Test
    fun aSlowModelIsATimeoutNotAnOutage() {
        val result = analyze { throw SocketTimeoutException("timeout") }
        assertEquals(AnalysisError.Timeout, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun aDnsFailureIsReportedAsOffline() {
        val result = analyze { throw UnknownHostException("no dns") }
        assertEquals(AnalysisError.Offline, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun aDroppedConnectionIsReportedAsOffline() {
        val result = analyze { throw IOException("connection reset") }
        assertEquals(AnalysisError.Offline, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun cancellationPropagatesRatherThanBecomingAFailure() {
        // Backing out of the capture screen must not surface an error snackbar.
        var thrown: Throwable? = null
        try {
            runBlocking {
                analyzerFor(FakeApi { throw CancellationException("user left") })
                    .analyze("hf_test", "DATA")
            }
        } catch (e: CancellationException) {
            thrown = e
        }
        assertTrue("CancellationException must not be swallowed", thrown is CancellationException)
    }

    // ---- replies with nothing usable in them --------------------------------

    @Test
    fun anEmptyReplyIsRejected() {
        val result = analyze { reply("") }
        assertEquals(AnalysisError.UnreadableResult, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun aReplyWithNoChoicesIsRejected() {
        val result = analyze { Response.success(ChatResponse(emptyList())) }
        assertEquals(AnalysisError.UnreadableResult, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun aNullChoiceListIsRejectedRatherThanThrowing() {
        val result = analyze { Response.success(ChatResponse(null)) }
        assertEquals(AnalysisError.UnreadableResult, result.exceptionOrNull()!!.analysisError)
    }

    @Test
    fun truncatedJsonIsRejected() {
        val result = analyze { reply("""{"foodName":"Half a rep""") }
        assertEquals(AnalysisError.UnreadableResult, result.exceptionOrNull()!!.analysisError)
    }

    // ---- permissive extraction, strict values -------------------------------

    @Test
    fun proseAroundTheObjectIsIgnored() {
        val result = analyze {
            reply("Certainly! Here you go:\n{\"foodName\":\"Toast\",\"calories\":180}\nHope that helps.")
        }
        assertEquals("Toast", result.getOrThrow().foodName)
    }

    @Test
    fun markdownFencesAreIgnored() {
        val result = analyze { reply("```json\n{\"foodName\":\"Soup\",\"calories\":210}\n```") }
        assertEquals("Soup", result.getOrThrow().foodName)
    }

    @Test
    fun anAbsurdValueIsClampedAndFlaggedRatherThanRejected() {
        // A usable estimate with one bad number is still worth showing the user,
        // with the caveat, rather than discarding the whole analysis.
        val result = analyze { reply("""{"foodName":"Apple","calories":999999999}""") }
        val nutrition = result.getOrThrow()
        assertEquals(NutritionBounds.MAX_CALORIES, nutrition.calories)
        assertTrue(nutrition.valuesClamped)
    }

    @Test
    fun aSelfContradictoryReplyIsFlaggedNotSilentlyCorrected() {
        val result = analyze {
            reply("""{"foodName":"Odd","calories":15,"proteinGrams":50,"carbsGrams":50,"fatGrams":30}""")
        }
        val nutrition = result.getOrThrow()
        assertEquals("the stated figure is kept", 15, nutrition.calories)
        assertTrue(nutrition.caloriesContradictMacros)
    }

    @Test
    fun aMissingCalorieFigureIsDerivedAndMarkedAsDerived() {
        val result = analyze {
            reply("""{"foodName":"Shake","proteinGrams":30,"carbsGrams":40,"fatGrams":5}""")
        }
        val nutrition = result.getOrThrow()
        assertEquals(325, nutrition.calories)
        assertTrue(nutrition.caloriesDerivedFromMacros)
    }

    @Test
    fun aReplyWithNoAssessmentGetsNoInventedOne() {
        val result = analyze { reply("""{"foodName":"Plain","calories":100}""") }
        assertNull(result.getOrThrow().assessment)
    }

    // ---- exactly one request per analysis -----------------------------------

    @Test
    fun oneAnalysisIssuesExactlyOneRequest() {
        // Vision calls are billable, and a silent retry is how the same meal ends
        // up logged twice.
        val api = FakeApi { httpError(500) }
        runBlocking { analyzerFor(api).analyze("hf_test", "DATA") }
        assertEquals(1, api.callCount)
    }
}
