package com.deepseek.dsh.runtime.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HostService : Service() {

    companion object {
        const val CHANNEL_ID = "dsh_host_runtime_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.deepseek.dsh.runtime.host.START"
        const val ACTION_STOP = "com.deepseek.dsh.runtime.host.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildForegroundNotification("DSH Agent 运行时保持后台运行")
                startForeground(NOTIFICATION_ID, notification)
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DSH Host Runtime",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保证本地 DSH Host 容器与任务后台运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DSH 本地运行时")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }
}
