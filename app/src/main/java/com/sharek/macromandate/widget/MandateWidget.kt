package com.sharek.macromandate.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sharek.macromandate.MainActivity
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.NutritionFormat
import kotlinx.coroutines.flow.first

const val EXTRA_OPEN_MANUAL_ENTRY = "open_manual_entry"
private val OpenManualEntryKey = ActionParameters.Key<Boolean>(EXTRA_OPEN_MANUAL_ENTRY)

class MandateWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val repository = MealRepository(database.mealDao())
        val preferences = MandatePreferences(context)

        val todayMeals = repository.getTodayMeals()
        val calorieTarget = preferences.calorieTargetFlow
        val initialMeals = todayMeals.first()
        val initialTarget = calorieTarget.first()

        provideContent {
            val meals by todayMeals.collectAsState(initialMeals)
            val target by calorieTarget.collectAsState(initialTarget)
            MandateWidgetContent(meals = meals, target = target)
        }
    }

    @Composable
    private fun MandateWidgetContent(meals: List<MealEntry>, target: Int) {
        val current = meals.sumOf { it.calories }
        val protein = NutritionFormat.formatGramsSum(meals.map { it.proteinGrams })
        val carbs = NutritionFormat.formatGramsSum(meals.map { it.carbsGrams })
        val fat = NutritionFormat.formatGramsSum(meals.map { it.fatGrams })
        val safeTarget = target.coerceAtLeast(0)
        val progress = if (safeTarget > 0) (current.toFloat() / safeTarget).coerceIn(0f, 1f) else 0f
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
                .clickable(
                    actionStartActivity(ComponentName(context, MainActivity::class.java))
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MACROMANDATE // TODAY",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = if (safeTarget > 0) "$current / $safeTarget kcal" else "$current kcal",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = "P: ${protein}g · C: ${carbs}g · F: ${fat}g",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp)
            )

            Spacer(modifier = GlanceModifier.height(10.dp))

            Button(
                text = "Log a meal",
                onClick = actionStartActivity(
                    ComponentName(context, MainActivity::class.java),
                    actionParametersOf(OpenManualEntryKey to true)
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}
