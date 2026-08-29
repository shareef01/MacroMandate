package com.sharek.macromandate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_entries")
data class MealEntity(
    @PrimaryKey
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
