package com.neko.neuecode.domain.jwxt

import com.neko.neuecode.data.local.schedule.WeekStartDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun paletteIndex_isStableAndInRange() {
        val keys = (0 until 200).map { "C$it:JX$it" }
        keys.forEach { key ->
            val index = CourseColorHasher.paletteIndex(key)
            assertTrue(index in 0 until CourseColorHasher.PALETTE_SIZE)
            assertEquals(index, CourseColorHasher.paletteIndex(key))
        }
        // A realistic spread of keys should use most of the palette.
        assertTrue(keys.map(CourseColorHasher::paletteIndex).toSet().size >= CourseColorHasher.PALETTE_SIZE - 2)
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
    fun actualWeek_isNullBeforeTermStart() {
        assertEquals(null, ScheduleWeekClock.actualWeek(termStartEpochDay = 10_000L, todayEpochDay = 9_999L))
        assertEquals(1, ScheduleWeekClock.actualWeek(termStartEpochDay = 10_000L, todayEpochDay = 10_000L))
        assertEquals(null, ScheduleWeekClock.actualWeek(termStartEpochDay = null, todayEpochDay = 10_000L))
    }

    @Test
    fun actualWeek_sundayFirstPutsSep6InWeek2WhenTermStartsMonday() {
        val termStart = ScheduleWeekClock.localEpochDay(2026, 8, 31)
        val sep5 = ScheduleWeekClock.localEpochDay(2026, 9, 5)
        val sep6 = ScheduleWeekClock.localEpochDay(2026, 9, 6)
        val aug30 = ScheduleWeekClock.localEpochDay(2026, 8, 30)
        assertEquals(1, ScheduleWeekClock.weekOf(termStart, sep6))
        assertEquals(2, ScheduleWeekClock.weekOf(termStart, sep6, WeekStartDay.SUNDAY))
        assertEquals(1, ScheduleWeekClock.weekOf(termStart, sep5, WeekStartDay.SUNDAY))
        assertEquals(1, ScheduleWeekClock.actualWeek(termStart, sep6))
        assertEquals(2, ScheduleWeekClock.actualWeek(termStart, sep6, WeekStartDay.SUNDAY))
        assertNull(ScheduleWeekClock.actualWeek(termStart, aug30, WeekStartDay.SUNDAY))
    }

    @Test
    fun localEpochDay_matchesUtcMidnightDivision() {
        val day = ScheduleWeekClock.localEpochDay(2026, 8, 24)
        assertEquals(day, ScheduleWeekClock.fromUtcMillis(day * 86_400_000L))
        assertEquals(1, ScheduleWeekClock.weekdayOf(ScheduleWeekClock.localEpochDay(2026, 8, 24)))
        assertEquals(4, ScheduleWeekClock.weekdayOf(ScheduleWeekClock.localEpochDay(2026, 8, 27)))
    }
}
