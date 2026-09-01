package com.neko.neuecode.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSettingsLayoutTest {

    @Test
    fun termList_capsVisibleRowsSoStartDateStaysOnScreen() {
        assertEquals(5, ScheduleSettingsLayout.VISIBLE_TERM_ROWS)
        assertEquals(160, ScheduleSettingsLayout.termListHeightDp(termCount = 8))
        assertEquals(160, ScheduleSettingsLayout.termListHeightDp(termCount = 12))
        assertTrue(ScheduleSettingsLayout.isTermListScrollable(termCount = 8))
    }

    @Test
    fun termList_shrinksWhenFewerTermsThanCap() {
        assertEquals(32, ScheduleSettingsLayout.termListHeightDp(termCount = 1))
        assertEquals(96, ScheduleSettingsLayout.termListHeightDp(termCount = 3))
        assertFalse(ScheduleSettingsLayout.isTermListScrollable(termCount = 5))
        assertEquals(0, ScheduleSettingsLayout.termListHeightDp(termCount = 0))
    }
}
