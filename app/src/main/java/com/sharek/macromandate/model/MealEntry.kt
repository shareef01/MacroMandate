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
    val isLiquid: Boolean
)
