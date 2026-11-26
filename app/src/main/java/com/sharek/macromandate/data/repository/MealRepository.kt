package com.sharek.macromandate.data.repository

import com.sharek.macromandate.data.local.MealDao
import com.sharek.macromandate.data.local.MealEntity
import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map

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
        const val MONTH_LENGTH_DAYS = 30
    }

    fun getPagedMeals(query: String, filterType: Int, sortOrder: Int): Flow<PagingData<MealEntry>> =
        (if (filterType == 1) dayBoundaries() else flowOf(0L))
            .flatMapLatest { todayMidnight -> pagedMeals(query, filterType, todayMidnight, sortOrder) }

    private fun pagedMeals(query: String, filterType: Int, todayMidnight: Long, sortOrder: Int): Flow<PagingData<MealEntry>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                val literal = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                mealDao.getMealsPaged(literal, filterType, todayMidnight, sortOrder)
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    fun getTodayMeals(): Flow<List<MealEntry>> =
        dayBoundaries().flatMapLatest { startOfToday ->
            mealDao.getTodayMeals(startOfToday).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    fun getMealsForDate(date: java.time.LocalDate, zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): Flow<List<MealEntry>> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return mealDao.getMealsInWindow(start, end).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getWeeklyMeals(): Flow<List<MealEntry>> =
        dayBoundaries().flatMapLatest { startOfToday ->
            mealDao.getMealsForLastWeek(windowStartFrom(startOfToday, WEEK_LENGTH_DAYS)).map { entities ->
                entities.map { it.toDomain() }
            }
        }

    fun getMonthlyMeals(): Flow<List<MealEntry>> =
        dayBoundaries().flatMapLatest { startOfToday ->
            mealDao.getMealsForLastWeek(windowStartFrom(startOfToday, MONTH_LENGTH_DAYS)).map { entities ->
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
            val now = java.time.ZonedDateTime.now()
            val date = now.toLocalDate()
            emit(date.atStartOfDay(now.zone).toInstant().toEpochMilli())
            val next = date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
            delay((next - now.toInstant().toEpochMilli()).coerceIn(1L, 60_000L))
        }
    }.distinctUntilChanged()

    private fun windowStartFrom(startOfToday: Long, days: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = startOfToday
            // -(days - 1): the window is inclusive of today.
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis

    suspend fun clearAllCoordinates(): Int = mealDao.clearAllCoordinates()

    suspend fun insertMeal(meal: MealEntry) {
        mealDao.insertMeal(meal.toEntity())
    }

    suspend fun insertMeals(meals: List<MealEntry>) {
        mealDao.insertMeals(meals.map { it.toEntity() })
    }

    suspend fun updateMeal(meal: MealEntry) {
        mealDao.updateMeal(meal.toEntity())
    }

    suspend fun deleteMeal(id: String) {
        mealDao.deleteById(id)
    }

    suspend fun getMealById(id: String): MealEntry? {
        return mealDao.getMealById(id)?.toDomain()
    }

    fun observeMeal(id: String): Flow<MealEntry?> = mealDao.observeMeal(id).map { it?.toDomain() }

    suspend fun getAllMealsAsList(): List<MealEntry> {
        // For CSV export, we might need all
        return mealDao.getAllMealsList().map { it.toDomain() }
    }

    suspend fun getLatestMeal(): MealEntry? = mealDao.getLatestMeal()?.toDomain()

    suspend fun getPhotoReferences() = mealDao.getPhotoReferences()


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
