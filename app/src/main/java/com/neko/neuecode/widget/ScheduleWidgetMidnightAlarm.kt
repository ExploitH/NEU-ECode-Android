package com.neko.neuecode.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import timber.log.Timber

object ScheduleWidgetMidnightAlarm {
    private const val REQUEST_CODE = 1201

    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = ScheduleWidgetRefreshPolicy.nextLocalMidnightMillis(
            System.currentTimeMillis(),
        )
        val intent = Intent(appContext, ScheduleWidgetClockReceiver::class.java).apply {
            action = ScheduleWidgetRefreshPolicy.midnightAction
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC,
            triggerAt,
            pendingIntent,
        )
        Timber.d("Next schedule widget midnight refresh set for %d", triggerAt)
    }
}
