package com.sharek.macromandate.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

data class OpenFoodFactsResponse(
    @SerializedName("code") val code: String?,
    @SerializedName("status") val status: Int,
    @SerializedName("product") val product: OffProduct?
)

data class OffProduct(
    @SerializedName("product_name") val productName: String?,
    @SerializedName("nutriments") val nutriments: OffNutriments?
)

data class OffNutriments(
    @SerializedName("energy-kcal_serving") val caloriesServing: Double?,
    @SerializedName("energy-kcal_100g") val calories100g: Double?,
    
    @SerializedName("proteins_serving") val proteinServing: Double?,
    @SerializedName("proteins_100g") val protein100g: Double?,
    
    @SerializedName("carbohydrates_serving") val carbsServing: Double?,
    @SerializedName("carbohydrates_100g") val carbs100g: Double?,
    
    @SerializedName("fat_serving") val fatServing: Double?,
    @SerializedName("fat_100g") val fat100g: Double?
)

interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): Response<OpenFoodFactsResponse>
}
