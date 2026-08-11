package com.sharek.macromandate.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object DossierExporter {

    suspend fun generateCsv(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("ID,Timestamp,FoodName,Calories,Protein,Carbs,Fat,IsLiquid\n")
        
        meals.forEach { meal ->
            val timestamp = dateFormat.format(Date(meal.timestamp))
            sb.append("${meal.id},")
            sb.append("$timestamp,")
            sb.append("\"${meal.foodName}\",")
            sb.append("${meal.calories},")
            sb.append("${meal.proteinGrams},")
            sb.append("${meal.carbsGrams},")
            sb.append("${meal.fatGrams},")
            sb.append("${meal.isLiquid}\n")
        }
        sb.toString()
    }
}
