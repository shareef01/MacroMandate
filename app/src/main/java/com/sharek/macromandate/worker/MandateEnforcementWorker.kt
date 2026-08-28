package com.sharek.macromandate.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.notification.NotificationManagerHelper
import com.sharek.macromandate.widget.MandateWidget
import kotlinx.coroutines.flow.first
import java.util.Calendar

class MandateEnforcementWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private companion object {
        const val TAG = "MandateEnforcementWorker"
        const val DAYTIME_START = 8
        const val DAYTIME_END = 22
        const val OVERDUE_AFTER_MILLIS = 6 * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val preferences = MandatePreferences(applicationContext)

        // The widget is otherwise only refreshed when the app itself writes,
        // so after midnight it kept showing yesterday's total until someone
        // opened the app. A stale calorie count is worse than no widget.
        runCatching { MandateWidget().updateAll(applicationContext) }
            .onFailure { Log.w(TAG, "Could not refresh the widget", it) }

        // Honor the user's enforcement toggle: no nagging when surveillance is disabled.
        if (!preferences.enforcementEnabledFlow.first()) {
            return Result.success()
        }

        val latestMeal = database.mealDao().getLatestMeal()
            // Someone who has never logged a meal is not "overdue". The previous
            // code defaulted the last-logged time to 0L, so a brand-new install
            // was told it had not logged a meal in a while on the worker's very
            // first run — before the user had done anything at all.
            ?: return Result.success()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in DAYTIME_START..DAYTIME_END) return Result.success()

        val sinceLastMeal = System.currentTimeMillis() - latestMeal.timestamp
        if (sinceLastMeal > OVERDUE_AFTER_MILLIS) {
            NotificationManagerHelper.postComplianceNotification(applicationContext)
        }

        return Result.success()
    }
}
