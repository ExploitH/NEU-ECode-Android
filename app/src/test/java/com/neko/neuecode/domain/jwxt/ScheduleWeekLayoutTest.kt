package com.neko.neuecode.domain.jwxt

import com.neko.neuecode.data.local.schedule.ScheduleSettings
import com.neko.neuecode.data.local.schedule.WeekStartDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleWeekLayoutTest {

    @Test
    fun weekStart_defaultsToSunday() {
        assertEquals(WeekStartDay.SUNDAY, ScheduleSettings().weekStartDay)
        assertEquals(WeekStartDay.SUNDAY, WeekStartDay.fromStored(null))
        assertEquals(WeekStartDay.SUNDAY, WeekStartDay.fromStored(""))
        assertEquals(WeekStartDay.MONDAY, WeekStartDay.fromStored("monday"))
    }

    @Test
    fun columnWeekdays_sundayFirstThenMondayThroughSaturday() {
        assertEquals(
            listOf(7, 1, 2, 3, 4, 5, 6),
            ScheduleWeekLayout.columnWeekdays(WeekStartDay.SUNDAY),
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            ScheduleWeekLayout.columnWeekdays(WeekStartDay.MONDAY),
        )
    }

    @Test
    fun columnIndex_putsSundayOnTheLeftWhenWeekStartsSunday() {
        assertEquals(0, ScheduleWeekLayout.columnIndex(weekday = 7, weekStartDay = WeekStartDay.SUNDAY))
        assertEquals(1, ScheduleWeekLayout.columnIndex(weekday = 1, weekStartDay = WeekStartDay.SUNDAY))
        assertEquals(6, ScheduleWeekLayout.columnIndex(weekday = 6, weekStartDay = WeekStartDay.SUNDAY))
        assertEquals(0, ScheduleWeekLayout.columnIndex(weekday = 1, weekStartDay = WeekStartDay.MONDAY))
        assertEquals(6, ScheduleWeekLayout.columnIndex(weekday = 7, weekStartDay = WeekStartDay.MONDAY))
    }

    @Test
    fun shortDateLabel_omitsLeadingZeros() {
        val aug31 = ScheduleWeekClock.localEpochDay(2026, 8, 31)
        val sep1 = ScheduleWeekClock.localEpochDay(2026, 9, 1)
        assertEquals("8.31", ScheduleWeekLayout.shortDateLabel(aug31))
        assertEquals("9.1", ScheduleWeekLayout.shortDateLabel(sep1))
    }

    @Test
    fun weekdayEpochDay_followsDisplayWeekFromTermStart() {
        // 2026-08-31 is Monday; academic week 1 is Mon 8.31 .. Sun 9.6
        val termStart = ScheduleWeekClock.localEpochDay(2026, 8, 31)
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 8, 31),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 1,
                weekday = 1,
                weekStartDay = WeekStartDay.MONDAY,
            ),
        )
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 9, 6),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 1,
                weekday = 7,
                weekStartDay = WeekStartDay.MONDAY,
            ),
        )
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 9, 7),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 2,
                weekday = 1,
                weekStartDay = WeekStartDay.MONDAY,
            ),
        )
        assertNull(
            ScheduleWeekLayout.weekdayEpochDay(
                termStartEpochDay = null,
                week = 1,
                weekday = 1,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
    }

    @Test
    fun weekdayEpochDay_sundayFirstUsesContinuousSundayToSaturday() {
        val termStart = ScheduleWeekClock.localEpochDay(2026, 8, 31)
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 8, 30),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 1,
                weekday = 7,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 8, 31),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 1,
                weekday = 1,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 9, 5),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 1,
                weekday = 6,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 9, 6),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 2,
                weekday = 7,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
        assertEquals(
            ScheduleWeekClock.localEpochDay(2026, 9, 7),
            ScheduleWeekLayout.weekdayEpochDay(
                termStart,
                week = 2,
                weekday = 1,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
    }

    @Test
    fun headers_sundayFirstIncludeShortDates() {
        val termStart = ScheduleWeekClock.localEpochDay(2026, 8, 31)
        val headers = ScheduleWeekLayout.headers(
            weekStartDay = WeekStartDay.SUNDAY,
            termStartEpochDay = termStart,
            week = 1,
            courseCounts = mapOf(1 to 2, 7 to 1),
        )
        assertEquals(listOf("日", "一", "二", "三", "四", "五", "六"), headers.map { it.label })
        assertEquals(
            listOf("8.30", "8.31", "9.1", "9.2", "9.3", "9.4", "9.5"),
            headers.map { it.dateLabel },
        )
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), headers.map { it.weekday })
        assertEquals(1, headers[0].courseCount)
        assertEquals(2, headers[1].courseCount)
        assertEquals(0, headers[2].courseCount)

        val week2 = ScheduleWeekLayout.headers(
            weekStartDay = WeekStartDay.SUNDAY,
            termStartEpochDay = termStart,
            week = 2,
            courseCounts = emptyMap(),
        )
        assertEquals(
            listOf("9.6", "9.7", "9.8", "9.9", "9.10", "9.11", "9.12"),
            week2.map { it.dateLabel },
        )
    }

    @Test
    fun displayWeekOfOccurrence_movesSundayToNextWeekWhenWeekStartsSunday() {
        val termStart = ScheduleWeekClock.localEpochDay(2026, 8, 31)
        assertEquals(
            1,
            ScheduleWeekLayout.displayWeekOfOccurrence(
                termStartEpochDay = termStart,
                academicWeek = 1,
                weekday = 1,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
        assertEquals(
            2,
            ScheduleWeekLayout.displayWeekOfOccurrence(
                termStartEpochDay = termStart,
                academicWeek = 1,
                weekday = 7,
                weekStartDay = WeekStartDay.SUNDAY,
            ),
        )
        assertEquals(
            1,
            ScheduleWeekLayout.displayWeekOfOccurrence(
                termStartEpochDay = termStart,
                academicWeek = 1,
                weekday = 7,
                weekStartDay = WeekStartDay.MONDAY,
            ),
        )
    }

    @Test
    fun headers_mondayFirstKeepMonToSunDates() {
        val termStart = ScheduleWeekClock.localEpochDay(2026, 8, 31)
        val headers = ScheduleWeekLayout.headers(
            weekStartDay = WeekStartDay.MONDAY,
            termStartEpochDay = termStart,
            week = 1,
            courseCounts = emptyMap(),
        )
        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), headers.map { it.label })
        assertEquals(
            listOf("8.31", "9.1", "9.2", "9.3", "9.4", "9.5", "9.6"),
            headers.map { it.dateLabel },
        )
    }

    @Test
    fun headers_omitDatesWithoutTermStart() {
        val headers = ScheduleWeekLayout.headers(
            weekStartDay = WeekStartDay.SUNDAY,
            termStartEpochDay = null,
            week = 1,
            courseCounts = emptyMap(),
        )
        assertEquals(listOf("日", "一", "二", "三", "四", "五", "六"), headers.map { it.label })
        assertEquals(List(7) { null }, headers.map { it.dateLabel })
    }
}
