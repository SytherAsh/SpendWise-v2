package com.spendwise.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.spendwise.R

/**
 * Foreground service lifecycle for ongoing SMS monitoring (docs/architecture.md Android
 * module map "SMS"). Runs continuously, with a persistent notification, so Android's Doze/App
 * Standby doesn't kill it. Starting/stopping this service in response to onboarding
 * completion or user settings is Epic 9's job (E9-S1-T5) — this class only owns its own
 * lifecycle once started.
 */
class SmsForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sms_monitoring_notification_title))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SMS Monitoring", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sms_monitoring"
    }
}
