package com.sharek.macromandate.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import com.sharek.macromandate.widget.MandateWidget
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Ensures the home screen widget refreshes at local midnight so a user checking
 * at 00:05 doesn't see yesterday's calories for hours until another meal is logged.
 */
class MidnightWidgetRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            MandateWidget().updateAll(applicationContext)
            schedule(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                schedule(applicationContext)
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "mandate_midnight_widget_refresh"
        private const val MAX_RETRIES = 3

        /**
         * Calculates milliseconds from [nowMillis] to the next local midnight,
         * with a 5-second buffer to ensure the day has officially rolled over.
         */
        fun millisUntilNextMidnight(nowMillis: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }
            return (calendar.timeInMillis - nowMillis).coerceAtLeast(1000L)
        }

        fun schedule(context: Context) {
            val delayMillis = millisUntilNextMidnight()
            val request = OneTimeWorkRequestBuilder<MidnightWidgetRefreshWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
