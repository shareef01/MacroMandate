package com.sharek.macromandate.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.sharek.macromandate.widget.MandateWidget
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class MidnightWidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val result = try {
            MandateWidget().updateAll(applicationContext)
            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRIES) return Result.retry()
            Result.failure()
        }

        val scheduledTime = inputData.getLong(SCHEDULED_TIME, 0L)
        schedule(applicationContext, Instant.ofEpochMilli(scheduledTime)).await()
        return result
    }

    companion object {
        private const val WORK_NAME = "mandate_midnight_widget_refresh"
        private const val SCHEDULED_TIME = "scheduled_time"
        private const val MAX_RETRIES = 3

        internal fun nextRefresh(now: Instant, zone: ZoneId): ZonedDateTime {
            val today = now.atZone(zone).toLocalDate()
            val todayRefresh = today.atStartOfDay(zone).plusSeconds(5)
            return if (todayRefresh.toInstant().isAfter(now)) todayRefresh
            else today.plusDays(1).atStartOfDay(zone).plusSeconds(5)
        }

        fun schedule(context: Context) {
            schedule(context, Instant.EPOCH)
        }

        private fun schedule(context: Context, earliest: Instant): Operation {
            val now = Instant.now()
            val refresh = nextRefresh(maxOf(now, earliest), ZoneId.systemDefault())
            val request = OneTimeWorkRequestBuilder<MidnightWidgetRefreshWorker>()
                .setInitialDelay(Duration.between(now, refresh.toInstant()).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(SCHEDULED_TIME to refresh.toInstant().toEpochMilli()))
                .build()

            return WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME-${refresh.toLocalDate()}",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
