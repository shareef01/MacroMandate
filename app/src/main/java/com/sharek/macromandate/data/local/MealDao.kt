package com.sharek.macromandate.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeal(meal: MealEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeals(meals: List<MealEntity>)

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Delete
    suspend fun deleteMeal(meal: MealEntity)

    @Query("""
        SELECT * FROM meal_entries
        WHERE (:query = '' OR foodName LIKE '%' || :query || '%' ESCAPE '\' OR assessment LIKE '%' || :query || '%' ESCAPE '\' OR id LIKE '%' || :query || '%' ESCAPE '\')
        AND (:filterType = 0
            OR (:filterType = 1 AND timestamp >= :todayMidnight)
            OR (:filterType = 2 AND proteinGrams >= 25)
            OR (:filterType = 3 AND calories >= 500)
            OR (:filterType = 4 AND isLiquid = 1)
            OR (:filterType = 5 AND (isRestricted = 1 OR isNightRefueling = 1)))
        ORDER BY
            CASE WHEN :sortOrder = 0 THEN timestamp END DESC,
            CASE WHEN :sortOrder = 1 THEN calories END DESC,
            CASE WHEN :sortOrder = 2 THEN proteinGrams END DESC,
            timestamp DESC, id ASC
    """)
    fun getMealsPaged(query: String, filterType: Int, todayMidnight: Long, sortOrder: Int): androidx.paging.PagingSource<Int, MealEntity>

    @Query("SELECT * FROM meal_entries WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayMeals(startOfDay: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal_entries WHERE timestamp >= :sevenDaysAgoTimestamp ORDER BY timestamp ASC")
    fun getMealsForLastWeek(sevenDaysAgoTimestamp: Long): Flow<List<MealEntity>>
    
    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun getMealById(id: String): MealEntity?

    @Query("SELECT * FROM meal_entries WHERE id = :id")
    fun observeMeal(id: String): Flow<MealEntity?>

    @Query("SELECT * FROM meal_entries ORDER BY timestamp DESC")
    suspend fun getAllMealsList(): List<MealEntity>

    @Query("SELECT * FROM meal_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMeal(): MealEntity?

    @Query("SELECT imageUri FROM meal_entries WHERE imageUri IS NOT NULL")
    suspend fun getPhotoReferences(): List<String>


    @Query("DELETE FROM meal_entries")
    suspend fun deleteAll()
}
