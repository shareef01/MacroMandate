package com.sharek.macromandate.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic enforcement check. WorkManager persists the schedule
 * across reboots, so this only needs to run once at startup and whenever the
 * enforcement toggle changes.
 */
object EnforcementScheduler {
    private const val WORK_NAME = "mandate_enforcement_periodic"
    private const val PERIOD_HOURS = 6L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MandateEnforcementWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
