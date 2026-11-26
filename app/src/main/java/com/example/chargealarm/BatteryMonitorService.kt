package com.example.chargealarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "charge_alarm_channel"
        const val NOTIFICATION_ID = 101
        const val EXTRA_TARGET_PERCENT = "target_percent"
        const val ACTION_STOP = "com.example.chargealarm.STOP_ALARM"

        var isMonitoring = false
        var currentTarget = 80
    }

    private var targetPercent = 80
    private var alarmTriggered = false
    private var ringtone: Ringtone? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                if (level >= 0 && scale > 0) {
                    val currentPct = (level * 100) / scale

                    if (isCharging && currentPct >= targetPercent && !alarmTriggered) {
                        alarmTriggered = true
                        triggerAlarm(currentPct)
                    } else if (!alarmTriggered) {
                        val stateText = if (isCharging) "Charging ($currentPct%)" else "Waiting for charger ($currentPct%)"
                        updateNotification("$stateText — Alarm set for $targetPercent%")
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarmAndService()
            return START_NOT_STICKY
        }

        targetPercent = intent?.getIntExtra(EXTRA_TARGET_PERCENT, 80) ?: 80
        currentTarget = targetPercent
        alarmTriggered = false
        isMonitoring = true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring battery — Target: $targetPercent%"))

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return START_STICKY
    }

    private fun triggerAlarm(reachedPct: Int) {
        updateNotification("Target reached: $reachedPct%! Tap Stop to silence.")

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            play()
        }
    }

    private fun stopAlarmAndService() {
        ringtone?.stop()
        ringtone = null
        isMonitoring = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Charge Alarm",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BatteryMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Charge Alarm Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop / Dismiss", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
        }
        ringtone?.stop()
        isMonitoring = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
