package com.sharek.macromandate.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sharek.macromandate.service.MandateSurveillanceService

class MandateBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Android 15 (API 35) forbids launching dataSync foreground services from
        // BOOT_COMPLETED; attempting it throws ForegroundServiceStartNotAllowedException.
        // The HUD re-arms the next time the user opens the app, and the periodic
        // enforcement check is restored by WorkManager's own boot handling.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        try {
            context.startForegroundService(
                Intent(context, MandateSurveillanceService::class.java)
            )
        } catch (e: Exception) {
            // Background-start restrictions vary by OEM and by device state at boot.
            // Never let the boot broadcast crash the app.
            Log.w("MandateBootReceiver", "Could not start surveillance service on boot", e)
        }
    }
}
