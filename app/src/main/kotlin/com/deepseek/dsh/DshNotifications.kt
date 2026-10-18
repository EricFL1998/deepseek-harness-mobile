package com.deepseek.dsh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object DshNotifications {
    private const val CHANNEL_ID = "dsh_session_events"
    private var nextId = 2000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "会话通知",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "DSH 会话完成与错误提醒" }
                )
            }
        }
    }

    fun notify(context: Context, title: String, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(nextId++, notification)
        }
    }
}
