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
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_reminders_description)
            }
            
            val hudChannel = NotificationChannel(
                HUD_CHANNEL_ID,
                context.getString(R.string.notification_channel_daily_total),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_daily_total_description)
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
            .setContentTitle(context.getString(R.string.notification_meal_overdue_title))
            .setContentText(context.getString(R.string.notification_meal_overdue_body))
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
