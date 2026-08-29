package com.sharek.macromandate.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sharek.macromandate.MainActivity
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.data.repository.MealRepository
import kotlinx.coroutines.flow.first

class MandateWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val repository = MealRepository(database.mealDao())
        val preferences = MandatePreferences(context)

        val todayMeals = repository.getTodayMeals().first()
        val calorieTarget = preferences.calorieTargetFlow.first()
        val currentCalories = todayMeals.sumOf { it.calories }

        provideContent {
            MandateWidgetContent(currentCalories, calorieTarget)
        }
    }

    @Composable
    private fun MandateWidgetContent(current: Int, target: Int) {
        val progress = (current.toFloat() / target).coerceIn(0f, 1f)
        val context = LocalContext.current
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MACROMANDATE // SURVEILLANCE",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            
            Spacer(modifier = GlanceModifier.height(8.dp))
            
            Text(
                text = "$current / $target KCAL",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            Spacer(modifier = GlanceModifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(10.dp)
            )
            
            Spacer(modifier = GlanceModifier.height(12.dp))
            
            Button(
                text = "LOG FUEL",
                onClick = actionStartActivity(ComponentName(context, MainActivity::class.java)),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}
