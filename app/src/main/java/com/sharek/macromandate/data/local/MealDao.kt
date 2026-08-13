package com.sharek.macromandate.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity)

    @Delete
    suspend fun deleteMeal(meal: MealEntity)

    @Query("SELECT * FROM meal_entries ORDER BY timestamp DESC")
    fun getAllMeals(): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal_entries WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayMeals(startOfDay: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal_entries WHERE timestamp >= :sevenDaysAgoTimestamp ORDER BY timestamp ASC")
    fun getMealsForLastWeek(sevenDaysAgoTimestamp: Long): Flow<List<MealEntity>>
    
    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM meal_entries")
    suspend fun deleteAll()
}
