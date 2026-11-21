package com.sharek.macromandate.network

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OpenRouterConfigurationTest {
    private val openRouter = "https://openrouter.ai/api/".toHttpUrl()
    private val huggingFace = "https://router.huggingface.co/".toHttpUrl()

    @Test
    fun separatesPreviouslySavedCredentialsByEndpoint() {
        assertEquals("analysis_key:https://openrouter.ai/api/", ApiConfig.credentialPreferenceName(openRouter))
        assertEquals("api_key", ApiConfig.credentialPreferenceName(huggingFace))
        assertTrue(ApiConfig.acceptsKey(" sk-or-v1-test ", openRouter))
        assertTrue(ApiConfig.acceptsKey("", openRouter))
        assertFalse(ApiConfig.acceptsKey("hf_test", openRouter))
        assertFalse(ApiConfig.acceptsKey("sk-or-", openRouter))
        assertFalse(ApiConfig.acceptsKey("sk-or-v1-test with spaces", openRouter))
        assertTrue(ApiConfig.acceptsKey("hf_test", huggingFace))
    }

    @Test
    fun sendsImageCompletionToOpenRouterApiPath() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"choices":[{"message":{"content":"estimate"}}]}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder()
            .baseUrl(openRouter)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatCompletionApi::class.java)
        val response = api.chatCompletion(
            ApiConfig.authHeader("sk-or-v1-test"),
            photoAnalysisRequest("openrouter/free", "Estimate this meal.", "YQ==", usesOpenRouter = true)
        )

        assertEquals("estimate", response.body()?.firstMessage())
        val request = response.raw().request
        assertEquals("https://openrouter.ai/api/v1/chat/completions", request.url.toString())
        assertEquals("Bearer sk-or-v1-test", request.header("Authorization"))
        val buffer = Buffer()
        checkNotNull(request.body).writeTo(buffer)
        val payload = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        assertEquals("openrouter/free", payload.get("model").asString)
        assertEquals(1_600, payload.get("max_tokens").asInt)
        assertFalse(payload.getAsJsonObject("reasoning").get("enabled").asBoolean)
        assertEquals("json_object", payload.getAsJsonObject("response_format").get("type").asString)
        val message = payload.getAsJsonArray("messages")[0].asJsonObject
        val image = message.getAsJsonArray("content")[1].asJsonObject
        assertEquals("image_url", image.get("type").asString)
        assertEquals("data:image/jpeg;base64,YQ==", image.getAsJsonObject("image_url").get("url").asString)
    }

    @Test
    fun customProvidersKeepTheirExistingPhotoParameters() {
        val request = photoAnalysisRequest("custom/vision", "Estimate this meal.", "YQ==", usesOpenRouter = false)
        assertEquals(600, request.maxTokens)
        assertEquals(null, request.reasoning)
    }
}
