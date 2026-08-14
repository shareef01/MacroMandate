package com.sharek.macromandate.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Hugging Face Inference Providers, OpenAI-compatible chat completions.
 *
 * This replaces the legacy `api-inference.huggingface.co/models/{id}` endpoint,
 * whose hostname no longer resolves at all. That older API took a single
 * `inputs` string and returned a list of `generated_text`; it had no way to
 * carry an image, so the previous approach of splicing a base64 data URI into
 * the prompt text was never going to reach a vision model.
 */
interface HuggingFaceApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>
}

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 600
)

data class ChatMessage(
    val role: String,
    val content: List<ContentPart>
)

/**
 * One part of a multimodal message. Gson omits nulls, so a text part serializes
 * as `{"type":"text","text":...}` and an image part as
 * `{"type":"image_url","image_url":{"url":...}}`.
 */
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
) {
    companion object {
        fun text(value: String) = ContentPart(type = "text", text = value)

        fun jpegImage(base64: String) = ContentPart(
            type = "image_url",
            imageUrl = ImageUrl("data:image/jpeg;base64,$base64")
        )
    }
}

data class ImageUrl(val url: String)

data class ChatResponse(
    val choices: List<ChatChoice>?
) {
    /** The assistant's reply, or empty when the provider returned no choices. */
    fun firstMessage(): String = choices?.firstOrNull()?.message?.content.orEmpty()
}

data class ChatChoice(
    val message: ChatResponseMessage?
)

data class ChatResponseMessage(
    val content: String?
)
