package com.sharek.macromandate.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface HuggingFaceApi {
    @POST("models/meta-llama/Llama-3.2-11B-Vision-Instruct")
    suspend fun analyzeImage(
        @Header("Authorization") token: String,
        @Body request: HuggingFaceRequest
    ): Response<List<HuggingFaceResponse>>
}

data class HuggingFaceRequest(
    val inputs: String,
    val parameters: Map<String, Any>? = null
)

data class HuggingFaceResponse(
    @SerializedName("generated_text")
    val generatedText: String
)
