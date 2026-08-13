package com.sharek.macromandate

import android.app.Application
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.notification.NotificationManagerHelper
import com.sharek.macromandate.worker.EnforcementScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MandateApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Ensure absolute surveillance channels are established before any system module starts
        NotificationManagerHelper.createNotificationChannel(this)
        // Schedule the periodic enforcement check (persists across reboots via WorkManager),
        // but respect a user who explicitly disabled enforcement — otherwise re-scheduling
        // here would undo their choice on every launch.
        appScope.launch {
            val enforcementEnabled = MandatePreferences(this@MandateApplication).enforcementEnabledFlow.first()
            if (enforcementEnabled) {
                EnforcementScheduler.schedule(this@MandateApplication)
            } else {
                EnforcementScheduler.cancel(this@MandateApplication)
            }
        }
    }
}
