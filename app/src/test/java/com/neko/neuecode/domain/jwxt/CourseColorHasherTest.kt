package com.neko.neuecode.domain.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseColorHasherTest {

    @Test
    fun hue_isStableForSameCourseKey() {
        val first = CourseColorHasher.hue("A1001:JX001")
        val second = CourseColorHasher.hue("A1001:JX001")
        assertEquals(first, second, 0.0f)
        assertTrue(first in 0f..359f)
    }

    @Test
    fun hue_differsForDifferentCourseKeys() {
        assertNotEquals(
            CourseColorHasher.hue("A1001:JX001"),
            CourseColorHasher.hue("B2002:JX009"),
        )
    }
}

class ScheduleWeekClockTest {

    @Test
    fun weekOf_defaultsToOneWithoutTermStart() {
        assertEquals(1, ScheduleWeekClock.weekOf(termStartEpochDay = null, todayEpochDay = 20_000L))
    }

    @Test
    fun weekOf_countsFullWeeksFromTermStart() {
        assertEquals(1, ScheduleWeekClock.weekOf(termStartEpochDay = 10_000L, todayEpochDay = 10_000L))
        assertEquals(1, ScheduleWeekClock.weekOf(termStartEpochDay = 10_000L, todayEpochDay = 10_006L))
        assertEquals(2, ScheduleWeekClock.weekOf(termStartEpochDay = 10_000L, todayEpochDay = 10_007L))
        assertEquals(1, ScheduleWeekClock.weekOf(termStartEpochDay = 10_000L, todayEpochDay = 9_999L))
    }

    @Test
    fun localEpochDay_matchesUtcMidnightDivision() {
        val day = ScheduleWeekClock.localEpochDay(2026, 8, 24)
        assertEquals(day, ScheduleWeekClock.fromUtcMillis(day * 86_400_000L))
        assertEquals(1, ScheduleWeekClock.weekdayOf(ScheduleWeekClock.localEpochDay(2026, 8, 24)))
        assertEquals(4, ScheduleWeekClock.weekdayOf(ScheduleWeekClock.localEpochDay(2026, 8, 27)))
    }
}
