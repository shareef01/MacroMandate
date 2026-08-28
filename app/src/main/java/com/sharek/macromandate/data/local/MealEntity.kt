package com.sharek.macromandate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Every read of this table filters or orders by timestamp (today's totals, the
// weekly window, the history list). Without an index each one was a full scan.
@Entity(
    tableName = "meal_entries",
    indices = [Index(value = ["timestamp"])]
)
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
    val isLiquid: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val assessment: String?,
    val isRestricted: Boolean,
    val isNightRefueling: Boolean
)
