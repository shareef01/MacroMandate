package com.sharek.macromandate.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sharek.macromandate.R

object NotificationManagerHelper {
    const val CHANNEL_ID = "mandate_enforcement"
    const val HUD_CHANNEL_ID = "mandate_surveillance_hud"
    private const val NOTIFICATION_ID = 1001
    const val HUD_NOTIFICATION_ID = 1002

    // minSdk is 29, so notification channels always exist; the SDK_INT guard
    // this used to carry could never be false.
    fun createNotificationChannel(context: Context) {
        run {
            // DEFAULT, not HIGH: IMPORTANCE_HIGH means a heads-up card that
            // interrupts whatever the user is doing. "You have not logged lunch"
            // does not warrant that, and an app that interrupts people about food
            // several times a day gets its notifications turned off entirely.
            val enforcementChannel = NotificationChannel(
                CHANNEL_ID,
                "Meal reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders when a meal has not been logged"
            }
            
            val hudChannel = NotificationChannel(
                HUD_CHANNEL_ID,
                "Daily total",
                NotificationManager.IMPORTANCE_LOW // Ongoing persistent info
            ).apply {
                description = "Ongoing notification showing today's calories"
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(enforcementChannel)
            notificationManager.createNotificationChannel(hudChannel)
        }
    }

    fun postComplianceNotification(context: Context) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Meal overdue")
            .setContentText("You haven't logged a meal in a while.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                // Handle missing permission gracefully
            }
        }
    }
}
