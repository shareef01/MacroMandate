package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object DossierExporter {

    // Excel/Sheets evaluate a cell as a formula when it opens with one of these.
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t')

    suspend fun generateCsv(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("ID,Timestamp,FoodName,Calories,Protein,Carbs,Fat,IsLiquid\n")

        meals.forEach { meal ->
            val timestamp = dateFormat.format(Date(meal.timestamp))
            sb.append("${meal.id},")
            sb.append("$timestamp,")
            sb.append("\"${escapeCsvField(meal.foodName)}\",")
            sb.append("${meal.calories},")
            sb.append("${meal.proteinGrams},")
            sb.append("${meal.carbsGrams},")
            sb.append("${meal.fatGrams},")
            sb.append("${meal.isLiquid}\n")
        }
        sb.toString()
    }

    internal fun escapeCsvField(field: String): String {
        // LLM-generated food names can contain quotes and line breaks; quotes are
        // doubled (RFC-4180) and newlines stripped so each record stays on one row.
        val flattened = field.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"")
        // foodName comes back from the model, so it is attacker-influencable via the
        // photo. Neutralize spreadsheet formula injection with a leading apostrophe.
        return if (flattened.isNotEmpty() && flattened[0] in FORMULA_TRIGGERS) {
            "'$flattened"
        } else {
            flattened
        }
    }
}
