package com.sharek.macromandate.data.repository

import com.sharek.macromandate.data.local.MealDao
import com.sharek.macromandate.data.local.MealEntity
import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MealRepositoryTest {

    private lateinit var fakeDao: FakeMealDao
    private lateinit var repository: MealRepository

    @Before
    fun setUp() {
        fakeDao = FakeMealDao()
        repository = MealRepository(fakeDao)
    }

    private fun createMeal(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
        calories: Int = 500,
        foodName: String = "Test Fuel"
    ) = MealEntry(
        id = id,
        timestamp = timestamp,
        imageUri = "content://evidence/$id.jpg",
        foodName = foodName,
        calories = calories,
        proteinGrams = 35f,
        carbsGrams = 50f,
        fatGrams = 15f,
        isLiquid = false,
        latitude = 37.7749,
        longitude = -122.4194,
        assessment = "TEST ASSESSMENT",
        isRestricted = false,
        isNightRefueling = false
    )

    @Test
    fun insertAndRetrieveAllMeals() = runBlocking {
        val meal1 = createMeal("m1", calories = 400, foodName = "Chicken and Rice")
        val meal2 = createMeal("m2", calories = 600, foodName = "Steak")

        repository.insertMeal(meal1)
        repository.insertMeal(meal2)

        val allMeals = repository.getAllMealsAsList()
        assertEquals(2, allMeals.size)
        assertTrue(allMeals.any { it.id == "m1" && it.foodName == "Chicken and Rice" })
        assertTrue(allMeals.any { it.id == "m2" && it.foodName == "Steak" })
    }

    @Test
    fun insertMultipleMealsBatch() = runBlocking {
        val meals = listOf(
            createMeal("b1", calories = 250),
            createMeal("b2", calories = 350),
            createMeal("b3", calories = 450)
        )

        repository.insertMeals(meals)

        val result = repository.getAllMealsAsList()
        assertEquals(3, result.size)
    }

    @Test
    fun updateMealModifiesExistingRecord() = runBlocking {
        val original = createMeal("update_test", calories = 500, foodName = "Original Name")
        repository.insertMeal(original)

        val updated = original.copy(foodName = "Updated Name", calories = 750)
        repository.updateMeal(updated)

        val result = repository.getAllMealsAsList()
        assertEquals(1, result.size)
        assertEquals("Updated Name", result[0].foodName)
        assertEquals(750, result[0].calories)
    }

    @Test
    fun deleteMealRemovesTargetRecord() = runBlocking {
        val meal1 = createMeal("del_1")
        val meal2 = createMeal("del_2")
        repository.insertMeals(listOf(meal1, meal2))

        repository.deleteMeal("del_1")

        val result = repository.getAllMealsAsList()
        assertEquals(1, result.size)
        assertEquals("del_2", result[0].id)
    }

    @Test
    fun getLatestMealReturnsMostRecentEntry() = runBlocking {
        val older = createMeal("older", timestamp = 1_000_000L)
        val newer = createMeal("newer", timestamp = 2_000_000L)

        repository.insertMeal(older)
        repository.insertMeal(newer)

        val latest = repository.getLatestMeal()
        assertNotNull(latest)
        assertEquals("newer", latest!!.id)
    }

    @Test
    fun deleteAllMealsClearsRepository() = runBlocking {
        repository.insertMeals(listOf(createMeal("1"), createMeal("2")))
        assertEquals(2, repository.getAllMealsAsList().size)

        repository.deleteAllMeals()
        assertEquals(0, repository.getAllMealsAsList().size)
    }

    @Test
    fun clearAllCoordinatesStripsLocationWithoutDeletingMeals() = runBlocking {
        val meal1 = createMeal("geo1", calories = 500, foodName = "Geotagged Lunch")
        val meal2 = createMeal("nogeo2", calories = 300, foodName = "Plain Breakfast").copy(
            latitude = null,
            longitude = null
        )
        repository.insertMeals(listOf(meal1, meal2))

        val clearedCount = repository.clearAllCoordinates()
        assertEquals(1, clearedCount)

        val all = repository.getAllMealsAsList()
        assertEquals(2, all.size)
        assertTrue(all.all { it.latitude == null && it.longitude == null })
    }
}

class FakeMealDao : MealDao {
    private val entries = MutableStateFlow<Map<String, MealEntity>>(emptyMap())

    override suspend fun insertMeal(meal: MealEntity) {
        entries.value = entries.value + (meal.id to meal)
    }

    override suspend fun insertMeals(meals: List<MealEntity>) {
        val newMap = entries.value.toMutableMap()
        meals.forEach { newMap[it.id] = it }
        entries.value = newMap
    }

    override suspend fun updateMeal(meal: MealEntity) {
        entries.value = entries.value + (meal.id to meal)
    }

    override suspend fun deleteMeal(meal: MealEntity) {
        entries.value = entries.value - meal.id
    }

    override suspend fun getAllMealsList(): List<MealEntity> =
        entries.value.values.sortedByDescending { it.timestamp }

    override suspend fun getMealById(id: String) = entries.value[id]

    override fun observeMeal(id: String): Flow<MealEntity?> = entries.map { it[id] }

    override suspend fun getPhotoReferences() = entries.value.values.mapNotNull { it.imageUri }


    override fun getMealsPaged(query: String, filterType: Int, todayMidnight: Long, sortOrder: Int):
        androidx.paging.PagingSource<Int, MealEntity> = error("Paging requires Room")

    override fun getTodayMeals(startOfDay: Long): Flow<List<MealEntity>> {
        return entries.map { map ->
            map.values.filter { it.timestamp >= startOfDay }.sortedByDescending { it.timestamp }
        }
    }

    override fun getMealsInWindow(startInclusive: Long, endExclusive: Long): Flow<List<MealEntity>> {
        return entries.map { map ->
            map.values.filter { it.timestamp in startInclusive until endExclusive }.sortedByDescending { it.timestamp }
        }
    }

    override fun getMealsForLastWeek(sevenDaysAgoTimestamp: Long): Flow<List<MealEntity>> {
        return entries.map { map ->
            map.values.filter { it.timestamp >= sevenDaysAgoTimestamp }.sortedBy { it.timestamp }
        }
    }

    override suspend fun clearAllCoordinates(): Int {
        var count = 0
        val updated = entries.value.mapValues { (_, entity) ->
            if (entity.latitude != null || entity.longitude != null) {
                count++
                entity.copy(latitude = null, longitude = null)
            } else {
                entity
            }
        }
        entries.value = updated
        return count
    }

    override suspend fun deleteById(id: String) {
        entries.value = entries.value - id
    }

    override suspend fun getLatestMeal(): MealEntity? {
        return entries.value.values.maxByOrNull { it.timestamp }
    }

    override suspend fun deleteAll() {
        entries.value = emptyMap()
    }
}
