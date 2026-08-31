package com.neko.neuecode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.neko.neuecode.MainActivity
import com.neko.neuecode.R
import com.neko.neuecode.data.local.schedule.JwxtScheduleCacheStore
import com.neko.neuecode.data.local.schedule.PrefsScheduleSettingsStore
import com.neko.neuecode.domain.jwxt.ScheduleWeekClock
import com.neko.neuecode.ui.navigation.MainDestinations

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
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (!ScheduleDayPagerPolicy.acceptsPagerAction(intent.action)) return
        val manager = AppWidgetManager.getInstance(context)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val ids = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            intArrayOf(widgetId)
        } else {
            manager.getAppWidgetIds(
                android.content.ComponentName(context, ScheduleWeekWidgetProvider::class.java),
            )
        }
        ids.forEach { id ->
            val current = ScheduleDayPagerStore.loadOffset(context, id)
            ScheduleDayPagerStore.saveOffset(
                context,
                id,
                ScheduleDayPagerPolicy.offsetAfter(intent.action, current),
            )
            render(context, manager, id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ScheduleDayPagerStore.clear(context, it) }
    }

    companion object {
        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val document = JwxtScheduleCacheStore(context).load()
            val settings = PrefsScheduleSettingsStore(context).load()
            val today = ScheduleWeekClock.todayEpochDay()
            val offset = ScheduleDayPagerStore.loadOffset(context, appWidgetId)
            val selectedDay = ScheduleDayPagerPolicy.selectedEpochDay(today, offset)
            val weekday = ScheduleWeekClock.weekdayOf(selectedDay)
            val week = ScheduleWeekClock.actualWeek(settings.termStartEpochDay, selectedDay)
            val title = when {
                document == null -> "暂无课表缓存"
                settings.termStartEpochDay == null -> "请先设定开学日"
                else -> ScheduleDayPagerPolicy.title(offset, weekday, week)
            }
            val cards = ScheduleWidgetPresentation.dayCards(document, week, weekday)
            val views = RemoteViews(context.packageName, R.layout.schedule_week_widget)
            views.setTextViewText(R.id.widget_schedule_kicker, "单日课表")
            views.setTextViewText(R.id.widget_schedule_title, title)
            views.removeAllViews(R.id.widget_day_cards)
            if (document == null) {
                views.setViewVisibility(R.id.widget_schedule_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_schedule_empty, "打开课表同步")
            } else if (settings.termStartEpochDay == null || week == null) {
                views.setViewVisibility(R.id.widget_schedule_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_schedule_empty, "开学日前不展示课表")
            } else if (cards.isEmpty()) {
                views.setViewVisibility(R.id.widget_schedule_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_schedule_empty, ScheduleWidgetPresentation.dayEmptyCopy)
            } else {
                views.setViewVisibility(R.id.widget_schedule_empty, View.GONE)
                cards.forEach { card ->
                    val row = RemoteViews(context.packageName, R.layout.schedule_day_class_card)
                    row.setTextViewText(R.id.widget_day_class_name, card.courseName)
                    val classroom = card.classroom.ifBlank { "地点未排" }
                    row.setTextViewText(
                        R.id.widget_day_class_meta,
                        "${card.timeLabel}  ${card.sectionLabel}  $classroom",
                    )
                    row.setInt(
                        R.id.widget_day_class_root,
                        "setBackgroundResource",
                        ScheduleWidgetPresentation.cardBackgrounds[card.backgroundResIndex],
                    )
                    views.addView(R.id.widget_day_cards, row)
                }
            }
            views.setOnClickPendingIntent(R.id.widget_schedule_root, pendingOpenApp(context, 1102 + appWidgetId))
            views.setOnClickPendingIntent(
                R.id.widget_day_prev,
                pendingPagerAction(context, appWidgetId, ScheduleDayPagerPolicy.prevAction, 2100 + appWidgetId),
            )
            views.setOnClickPendingIntent(
                R.id.widget_day_today,
                pendingPagerAction(context, appWidgetId, ScheduleDayPagerPolicy.todayAction, 2200 + appWidgetId),
            )
            views.setOnClickPendingIntent(
                R.id.widget_day_next,
                pendingPagerAction(context, appWidgetId, ScheduleDayPagerPolicy.nextAction, 2300 + appWidgetId),
            )
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}

private fun pendingOpenApp(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_START_ROUTE, MainDestinations.widgetStartRoute)
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun pendingPagerAction(
    context: Context,
    appWidgetId: Int,
    action: String,
    requestCode: Int,
): PendingIntent {
    val intent = Intent(context, ScheduleWeekWidgetProvider::class.java).apply {
        this.action = action
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
