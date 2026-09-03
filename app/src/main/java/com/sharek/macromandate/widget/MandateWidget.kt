package com.sharek.macromandate.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sharek.macromandate.MainActivity
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.data.repository.MealRepository
import kotlinx.coroutines.flow.first
import com.sharek.macromandate.R

/** Also the Intent extra key `MainActivity.onCreate` reads to open manual entry directly. */
const val EXTRA_OPEN_MANUAL_ENTRY = "open_manual_entry"
private val OpenManualEntryKey = ActionParameters.Key<Boolean>(EXTRA_OPEN_MANUAL_ENTRY)

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
        // A zero target produced Infinity (or NaN at 0/0), and coerceIn passes NaN
        // straight through to the progress indicator.
        val progress = if (target > 0) {
            (current.toFloat() / target).coerceIn(0f, 1f)
        } else {
            0f
        }
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = context.getString(R.string.widget_title),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = if (target > 0) {
                    context.getString(R.string.widget_progress, current, target)
                } else {
                    context.getString(R.string.widget_progress_no_target, current)
                },
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
                text = context.getString(R.string.widget_log_meal),
                // Previously opened the app to the dashboard, one more tap
                // away from the action the button's own label promised. The
                // extra is read in MainActivity.onCreate and opens the manual
                // entry dialog directly.
                onClick = actionStartActivity(
                    ComponentName(context, MainActivity::class.java),
                    actionParametersOf(OpenManualEntryKey to true)
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}
