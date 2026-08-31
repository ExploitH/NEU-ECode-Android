package com.neko.neuecode.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class ScheduleWidgetRefreshPolicyTest {

    @Test
    fun clockEvents_refreshForDateTimeAndTimezoneChangesOnly() {
        assertEquals(
            setOf(
                "android.intent.action.DATE_CHANGED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
            ),
            ScheduleWidgetRefreshPolicy.clockActions,
        )
        assertTrue(ScheduleWidgetRefreshPolicy.acceptsClockAction("android.intent.action.DATE_CHANGED"))
        assertTrue(ScheduleWidgetRefreshPolicy.acceptsClockAction("android.intent.action.TIME_SET"))
        assertTrue(ScheduleWidgetRefreshPolicy.acceptsClockAction("android.intent.action.TIMEZONE_CHANGED"))
        assertFalse(ScheduleWidgetRefreshPolicy.acceptsClockAction(null))
        assertFalse(ScheduleWidgetRefreshPolicy.acceptsClockAction("android.intent.action.TIME_TICK"))
    }

    @Test
    fun fallbackRefresh_isLocalAndRunsEverySixHours() {
        assertEquals(6L, ScheduleWidgetRefreshPolicy.fallbackIntervalHours)
        assertFalse(ScheduleWidgetRefreshPolicy.requiresNetwork)
    }

    @Test
    fun nextMidnightRefresh_rollsToTheNextLocalCalendarDay() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = GregorianCalendar(utc).apply {
            set(2026, Calendar.AUGUST, 31, 12, 34, 56)
            set(Calendar.MILLISECOND, 789)
        }
        val expected = GregorianCalendar(utc).apply {
            set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(
            expected.timeInMillis,
            ScheduleWidgetRefreshPolicy.nextLocalMidnightMillis(now.timeInMillis, utc),
        )
        assertTrue(
            ScheduleWidgetRefreshPolicy.acceptsRefreshAction(
                ScheduleWidgetRefreshPolicy.midnightAction,
            ),
        )
        assertTrue(
            ScheduleWidgetRefreshPolicy.acceptsRefreshAction(
                ScheduleWidgetRefreshPolicy.classBoundaryAction,
            ),
        )
    }
}
