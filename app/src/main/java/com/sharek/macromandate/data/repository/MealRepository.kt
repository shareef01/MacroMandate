package com.sharek.macromandate.data.repository

import com.sharek.macromandate.data.local.MealDao
import com.sharek.macromandate.data.local.MealEntity
import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class MealRepository(private val mealDao: MealDao) {

    fun getAllMeals(): Flow<List<MealEntry>> {
        return mealDao.getAllMeals().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getTodayMeals(): Flow<List<MealEntry>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return mealDao.getTodayMeals(calendar.timeInMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getWeeklyMeals(): Flow<List<MealEntry>> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return mealDao.getMealsForLastWeek(calendar.timeInMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun insertMeal(meal: MealEntry) {
        mealDao.insertMeal(meal.toEntity())
    }

    suspend fun deleteMeal(id: String) {
        mealDao.deleteById(id)
    }

    private fun MealEntity.toDomain() = MealEntry(
        id = id,
        timestamp = timestamp,
        imageUri = imageUri,
        foodName = foodName,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        isLiquid = isLiquid
    )

    private fun MealEntry.toEntity() = MealEntity(
        id = id,
        timestamp = timestamp,
        imageUri = imageUri,
        foodName = foodName,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        isLiquid = isLiquid
    )
}
