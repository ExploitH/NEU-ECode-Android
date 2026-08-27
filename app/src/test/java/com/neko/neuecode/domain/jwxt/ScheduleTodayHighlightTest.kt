package com.neko.neuecode.domain.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTodayHighlightTest {

    @Test
    fun weekdayToMark_onlyWhenViewingActualWeek() {
        assertEquals(
            4,
            ScheduleTodayHighlight.weekdayToMark(
                selectedWeek = 12,
                actualWeek = 12,
                todayWeekday = 4,
            ),
        )
        assertEquals(
            0,
            ScheduleTodayHighlight.weekdayToMark(
                selectedWeek = 3,
                actualWeek = 12,
                todayWeekday = 4,
            ),
        )
    }

    @Test
    fun weekdayToMark_noneWithoutTermStart() {
        assertEquals(
            0,
            ScheduleTodayHighlight.weekdayToMark(
                selectedWeek = 1,
                actualWeek = null,
                todayWeekday = 4,
            ),
        )
    }

    @Test
    fun todayPaneWeek_ignoresSelectedWeek() {
        assertEquals(12, ScheduleTodayHighlight.todayPaneWeek(actualWeek = 12, selectedWeek = 3))
        assertNull(ScheduleTodayHighlight.todayPaneWeek(actualWeek = null, selectedWeek = 3))
    }
}
