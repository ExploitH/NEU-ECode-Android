package com.neko.neuecode.widget

import android.content.Context

object ScheduleDayPagerStore {
    private const val PREFS = "schedule_day_pager_widget"
    private const val KEY_OFFSET = "day_offset"

    fun loadOffset(context: Context, appWidgetId: Int): Int {
        return ScheduleDayPagerPolicy.normalizeOffset(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(key(appWidgetId), 0),
        )
    }

    fun saveOffset(context: Context, appWidgetId: Int, offset: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(key(appWidgetId), offset)
            .apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(appWidgetId))
            .apply()
    }

    private fun key(appWidgetId: Int): String = "$KEY_OFFSET:$appWidgetId"
}
