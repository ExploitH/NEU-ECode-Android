package com.neko.neuecode.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPagerPrefetchTest {
    @Test
    fun prefetch_isOneNeighbourWeek() {
        assertEquals(1, WEEK_PAGER_PREFETCH)
    }
}
