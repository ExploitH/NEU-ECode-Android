package com.neko.neuecode.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.neko.neuecode.data.local.schedule.JwxtScheduleCacheStore
import com.neko.neuecode.data.local.schedule.PrefsScheduleSettingsStore
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleWeekClock
import timber.log.Timber
import java.util.Calendar

object ScheduleWidgetMidnightAlarm {
    private const val MIDNIGHT_REQUEST_CODE = 1201
    private const val CLASS_BOUNDARY_REQUEST_CODE = 1202

    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        scheduleAt(
            appContext,
            MIDNIGHT_REQUEST_CODE,
            ScheduleWidgetRefreshPolicy.midnightAction,
            ScheduleWidgetRefreshPolicy.nextLocalMidnightMillis(System.currentTimeMillis()),
        )
        scheduleNextClassBoundary(appContext)
    }

    fun scheduleNextClassBoundary(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(
            appContext,
            CLASS_BOUNDARY_REQUEST_CODE,
            ScheduleWidgetRefreshPolicy.classBoundaryAction,
        )
        val triggerAt = nextClassBoundaryMillis(appContext) ?: run {
            alarmManager.cancel(pendingIntent)
            return
        }
        scheduleAt(
            appContext,
            CLASS_BOUNDARY_REQUEST_CODE,
            ScheduleWidgetRefreshPolicy.classBoundaryAction,
            triggerAt,
        )
    }

    private fun nextClassBoundaryMillis(context: Context): Long? {
        val document = JwxtScheduleCacheStore(context).load() ?: return null
        val settings = PrefsScheduleSettingsStore(context).load()
        val today = ScheduleWeekClock.todayEpochDay()
        val weekday = ScheduleWeekClock.todayWeekday()
        val actualWeek = ScheduleWeekClock.actualWeek(settings.termStartEpochDay, today) ?: return null
        val nowMinutes = ScheduleWidgetPresentation.currentMinutesOfDay()
        val items = SchedulePresentation.todayItems(document, weekday, actualWeek)
        val nextMinutes = ScheduleWidgetPresentation.nextRefreshMinutes(items, nowMinutes) ?: return null
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, nextMinutes / 60)
            set(Calendar.MINUTE, nextMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun scheduleAt(context: Context, requestCode: Int, action: String, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC,
            triggerAt,
            pendingIntent(context, requestCode, action),
        )
        Timber.d("Next schedule widget alarm action=%s at %d", action, triggerAt)
    }

    private fun pendingIntent(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, ScheduleWidgetClockReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
