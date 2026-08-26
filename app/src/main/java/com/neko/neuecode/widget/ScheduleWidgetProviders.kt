package com.neko.neuecode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.neko.neuecode.MainActivity
import com.neko.neuecode.R
import com.neko.neuecode.data.local.schedule.JwxtScheduleCacheStore
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleWeekClock
import java.util.Calendar

class ScheduleTodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val document = JwxtScheduleCacheStore(context).load()
        val todayEpochDay = todayEpochDay()
        val weekday = ScheduleWeekClock.weekdayOf(todayEpochDay)
        val week = ScheduleWeekClock.weekOf(null, todayEpochDay)
        val items = document?.let { SchedulePresentation.todayItems(it, weekday, week) }.orEmpty()
        val lines = if (items.isEmpty()) {
            "今天没有课\n点按打开课表"
        } else {
            items.take(4).joinToString("\n") { item ->
                "${item.startTime} ${item.courseName} ${item.classroom}".trim()
            }
        }
        val open = pendingOpenApp(context)
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.schedule_today_widget)
            views.setTextViewText(R.id.widget_schedule_title, "课表·今日")
            views.setTextViewText(R.id.widget_schedule_body, lines)
            views.setOnClickPendingIntent(R.id.widget_schedule_root, open)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class ScheduleWeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val document = JwxtScheduleCacheStore(context).load()
        val week = ScheduleWeekClock.weekOf(null, todayEpochDay())
        val cells = document?.let { SchedulePresentation.cellsForWeek(it, week) }.orEmpty()
        val body = if (cells.isEmpty()) {
            "本周暂无课表缓存\n点按打开课表"
        } else {
            cells.take(8).joinToString("\n") { cell ->
                "周${cell.weekday} 第${cell.startSection}-${cell.endSection}节 ${cell.courseName}"
            }
        }
        val open = pendingOpenApp(context)
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.schedule_week_widget)
            views.setTextViewText(R.id.widget_schedule_title, "课表·本周")
            views.setTextViewText(R.id.widget_schedule_body, body)
            views.setOnClickPendingIntent(R.id.widget_schedule_root, open)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

private fun pendingOpenApp(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        1101,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun todayEpochDay(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis / 86_400_000L
}
