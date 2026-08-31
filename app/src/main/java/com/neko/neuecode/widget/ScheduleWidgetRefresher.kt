package com.neko.neuecode.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleWidgetRefresher @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext

    fun refresh() {
        Timber.d("Refreshing schedule widgets from local cache")
        ScheduleWidgetUpdater.updateAll(appContext)
    }
}

object ScheduleWidgetUpdater {
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        updateTodayWidgets(appContext, manager)
        updateWeekWidgets(appContext, manager)
    }

    private fun updateTodayWidgets(context: Context, manager: AppWidgetManager) {
        val ids = manager.getAppWidgetIds(
            ComponentName(context, ScheduleTodayWidgetProvider::class.java),
        )
        if (ids.isNotEmpty()) {
            ScheduleTodayWidgetProvider().onUpdate(context, manager, ids)
            Timber.d("Refreshed %d today schedule widget(s)", ids.size)
        }
    }

    private fun updateWeekWidgets(context: Context, manager: AppWidgetManager) {
        val ids = manager.getAppWidgetIds(
            ComponentName(context, ScheduleWeekWidgetProvider::class.java),
        )
        if (ids.isNotEmpty()) {
            ScheduleWeekWidgetProvider().onUpdate(context, manager, ids)
            Timber.d("Refreshed %d week schedule widget(s)", ids.size)
        }
    }
}
