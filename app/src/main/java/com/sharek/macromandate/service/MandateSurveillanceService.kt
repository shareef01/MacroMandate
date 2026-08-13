package com.sharek.macromandate.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sharek.macromandate.MainActivity
import com.sharek.macromandate.data.local.AppDatabase
import com.sharek.macromandate.data.pref.MandatePreferences
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.notification.NotificationManagerHelper
import com.sharek.macromandate.util.ComplianceEngine
import com.sharek.macromandate.viewmodel.ComplianceStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class MandateSurveillanceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var complianceObserverStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationManagerHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationManagerHelper.HUD_NOTIFICATION_ID,
            createInitialNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (!complianceObserverStarted) {
            complianceObserverStarted = true
            observeCompliance()
        }

        return START_STICKY
    }

    /**
     * Android 15+ imposes a 6-hour time limit on dataSync foreground services.
     * When the system enforces it, shut down cleanly instead of leaking the service.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationManagerHelper.HUD_CHANNEL_ID)
            .setContentTitle("MACROMANDATE // ACTIVE WATCH")
            .setContentText("INITIALIZING SURVEILLANCE...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun observeCompliance() {
        val database = AppDatabase.getDatabase(this)
        val repository = MealRepository(database.mealDao())
        val preferences = MandatePreferences(this)

        serviceScope.launch {
            combine(
                repository.getTodayMeals(),
                preferences.calorieTargetFlow
            ) { meals, target ->
                val current = meals.sumOf { it.calories }
                val score = ComplianceEngine.calculateScore(meals, target)
                val status = ComplianceEngine.statusFor(score)
                Triple(current, target, status)
            }.collect { (current, target, status) ->
                updateNotification(current, target, status)
            }
        }
    }

    private fun updateNotification(current: Int, target: Int, status: ComplianceStatus) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (status) {
            ComplianceStatus.EXEMPLARY -> "STATUS: EXEMPLARY. THE STATE IS PLEASED."
            ComplianceStatus.ACCEPTABLE -> "STATUS: ACCEPTABLE. OPTIMIZATION REQUIRED."
            ComplianceStatus.SUBVERSIVE -> "STATUS: SUBVERSIVE. PRIVILEGES REVOKED."
            ComplianceStatus.CRISIS -> "STATUS: CRISIS. PLEAD FOR LENIENCY."
            ComplianceStatus.LOCKED -> "STATUS: LOCKED. ASSET SEIZED."
        }

        val notification = NotificationCompat.Builder(this, NotificationManagerHelper.HUD_CHANNEL_ID)
            .setContentTitle("MACROMANDATE // SURVEILLANCE")
            .setContentText("$current / $target KCAL | $statusText")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (status == ComplianceStatus.SUBVERSIVE) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setProgress(target, current, false)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NotificationManagerHelper.HUD_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
