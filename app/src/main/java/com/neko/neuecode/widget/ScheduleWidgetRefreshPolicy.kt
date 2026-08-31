package com.neko.neuecode.widget

import java.util.Calendar
import java.util.TimeZone

object ScheduleWidgetRefreshPolicy {
    const val midnightAction = "com.neko.neuecode.widget.ACTION_LOCAL_MIDNIGHT"
    const val classBoundaryAction = "com.neko.neuecode.widget.ACTION_CLASS_BOUNDARY"

    val clockActions: Set<String> = setOf(
        "android.intent.action.DATE_CHANGED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIMEZONE_CHANGED",
    )

    const val fallbackIntervalHours: Long = 6L
    const val requiresNetwork: Boolean = false

    fun acceptsClockAction(action: String?): Boolean = action in clockActions

    fun acceptsRefreshAction(action: String?): Boolean {
        return acceptsClockAction(action) || action == midnightAction || action == classBoundaryAction
    }

    fun nextLocalMidnightMillis(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
