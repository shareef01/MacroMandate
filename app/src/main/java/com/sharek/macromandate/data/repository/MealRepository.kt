package com.sharek.macromandate.data.repository

import com.sharek.macromandate.data.local.MealDao
import com.sharek.macromandate.data.local.MealEntity
import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class MealRepository(private val mealDao: MealDao) {

    companion object {
        /**
         * Length of the "week" used for both the compliance score and the weekly
         * chart, counting today. These must agree: the lookback was previously 7
         * days *before* today (8 days of data) while the chart drew 7 bars, so the
         * oldest day moved the score without ever being shown.
         */
        const val WEEK_LENGTH_DAYS = 7
    }

    fun getAllMeals(): Flow<List<MealEntry>> {
        return mealDao.getAllMeals().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getTodayMeals(): Flow<List<MealEntry>> =
        dayBoundaries().flatMapLatest { startOfToday ->
            mealDao.getTodayMeals(startOfToday).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    fun getWeeklyMeals(): Flow<List<MealEntry>> =
        dayBoundaries().flatMapLatest { startOfToday ->
            mealDao.getMealsForLastWeek(weekStartFrom(startOfToday)).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    /**
     * Emits the current start-of-day, then re-emits at every midnight.
     *
     * The cutoff must not be captured once when the flow is built: these flows are
     * collected for the lifetime of the ViewModel and of the long-running
     * surveillance service, so a fixed cutoff keeps reporting yesterday's totals
     * after the day rolls over.
     */
    private fun dayBoundaries(): Flow<Long> = flow {
        while (true) {
            val today = startOfTodayCalendar()
            emit(today.timeInMillis)
            // Adding a day via Calendar rather than 24h of millis keeps this correct
            // across DST transitions.
            val nextMidnight = (today.clone() as Calendar)
                .apply { add(Calendar.DAY_OF_YEAR, 1) }
                .timeInMillis
            delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(1L))
        }
    }

    private fun startOfTodayCalendar(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun weekStartFrom(startOfToday: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = startOfToday
            // -(WEEK_LENGTH_DAYS - 1): the window is inclusive of today.
            add(Calendar.DAY_OF_YEAR, -(WEEK_LENGTH_DAYS - 1))
        }.timeInMillis

    suspend fun insertMeal(meal: MealEntry) {
        mealDao.insertMeal(meal.toEntity())
    }

    suspend fun deleteMeal(id: String) {
        mealDao.deleteById(id)
    }

    suspend fun deleteAllMeals() {
        mealDao.deleteAll()
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
        isLiquid = isLiquid,
        latitude = latitude,
        longitude = longitude,
        assessment = assessment,
        isRestricted = isRestricted,
        isNightRefueling = isNightRefueling
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
        isLiquid = isLiquid,
        latitude = latitude,
        longitude = longitude,
        assessment = assessment,
        isRestricted = isRestricted,
        isNightRefueling = isNightRefueling
    )
}
