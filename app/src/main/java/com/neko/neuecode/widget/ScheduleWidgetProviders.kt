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
import com.neko.neuecode.data.local.schedule.PrefsScheduleSettingsStore
import com.neko.neuecode.domain.jwxt.ScheduleWeekClock

class ScheduleTodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val document = JwxtScheduleCacheStore(context).load()
        val settings = PrefsScheduleSettingsStore(context).load()
        val today = ScheduleWeekClock.todayEpochDay()
        val weekday = ScheduleWeekClock.todayWeekday()
        val actualWeek = ScheduleWeekClock.actualWeek(settings.termStartEpochDay, today)
        val lines = ScheduleWidgetPresentation.todayLines(document, actualWeek, weekday)
        val open = pendingOpenApp(context, 1101)
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.schedule_today_widget)
            views.setTextViewText(R.id.widget_schedule_kicker, "今日课表")
            views.setTextViewText(
                R.id.widget_schedule_title,
                ScheduleWidgetPresentation.todaySubtitle(actualWeek, weekday),
            )
            views.setTextViewText(R.id.widget_schedule_body, lines.joinToString("\n"))
            views.setOnClickPendingIntent(R.id.widget_schedule_root, open)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class ScheduleWeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val document = JwxtScheduleCacheStore(context).load()
        val settings = PrefsScheduleSettingsStore(context).load()
        val today = ScheduleWeekClock.todayEpochDay()
        val actualWeek = ScheduleWeekClock.actualWeek(settings.termStartEpochDay, today)
        val title = if (actualWeek == null) "学期尚未开始" else "第${actualWeek}周"
        val counts = if (actualWeek == null) {
            "请在课表设定开学日后再看本周"
        } else {
            val dayCounts = ScheduleWidgetPresentation.weekDayCounts(document, actualWeek)
            val names = listOf("一", "二", "三", "四", "五", "六", "日")
            names.zip(dayCounts).joinToString("  ") { (name, count) ->
                if (count == 0) "$name·无" else "$name·$count"
            }
        }
        val body = if (actualWeek == null) {
            "开学日前不展示周课表"
        } else {
            ScheduleWidgetPresentation.weekLines(document, actualWeek, limit = 5).joinToString("\n")
        }
        val open = pendingOpenApp(context, 1102)
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.schedule_week_widget)
            views.setTextViewText(R.id.widget_schedule_kicker, "本周课表")
            views.setTextViewText(R.id.widget_schedule_title, title)
            views.setTextViewText(R.id.widget_schedule_counts, counts)
            views.setTextViewText(R.id.widget_schedule_body, body)
            views.setOnClickPendingIntent(R.id.widget_schedule_root, open)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

private fun pendingOpenApp(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
