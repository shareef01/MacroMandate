package com.sharek.macromandate.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.notification.NotificationManagerHelper
import kotlinx.coroutines.flow.first
import java.util.Calendar

class MandateEnforcementWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val preferences = MandatePreferences(applicationContext)

        // Honor the user's enforcement toggle: no nagging when surveillance is disabled.
        if (!preferences.enforcementEnabledFlow.first()) {
            return Result.success()
        }

        val latestMeal = database.mealDao().getAllMeals().first().firstOrNull()

        val currentTime = System.currentTimeMillis()
        val sixHoursInMillis = 6 * 60 * 60 * 1000L

        // Check if it's daytime (e.g., 8 AM to 10 PM)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val isDaytime = hour in 8..22

        if (isDaytime) {
            val lastLoggedTime = latestMeal?.timestamp ?: 0L
            if (currentTime - lastLoggedTime > sixHoursInMillis) {
                NotificationManagerHelper.postComplianceNotification(applicationContext)
            }
        }

        return Result.success()
    }
}
