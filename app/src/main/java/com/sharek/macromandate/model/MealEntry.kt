package com.sharek.macromandate.model

data class MealEntry(
    val id: String,
    val timestamp: Long,
    val imageUri: String?,
    val foodName: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val isLiquid: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val assessment: String? = null,
    val isRestricted: Boolean = false,
    val isNightRefueling: Boolean = false
)
