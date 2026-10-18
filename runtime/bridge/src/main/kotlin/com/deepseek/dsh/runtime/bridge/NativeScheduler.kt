package com.deepseek.dsh.runtime.bridge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * v4.0 设计文档 §10.3: 原生调度
 * - Host 保留 schedule 语义；到点由原生 AlarmManager 唤醒运行时并触发 Host 执行
 * - 幂等键 (scheduleId, scheduledAt)
 */
object NativeScheduler {

    const val ACTION_TRIGGER_SCHEDULE = "com.deepseek.dsh.runtime.TRIGGER_SCHEDULE"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    fun scheduleTask(
        context: Context,
        scheduleId: String,
        triggerAtMillis: Long
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(ACTION_TRIGGER_SCHEDULE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_SCHEDULED_AT, triggerAtMillis)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            flags
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelTask(context: Context, scheduleId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_TRIGGER_SCHEDULE).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, scheduleId.hashCode(), intent, flags)
        if (pendingIntent != null) {
            am.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
