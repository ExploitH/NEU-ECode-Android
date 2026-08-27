package com.neko.neuecode.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPagerIndexTest {

    @Test
    fun pageIndex_clampsToFirstAndLastWeek() {
        assertEquals(0, WeekPagerIndex.pageOf(week = 0, maxWeek = 20))
        assertEquals(0, WeekPagerIndex.pageOf(week = 1, maxWeek = 20))
        assertEquals(19, WeekPagerIndex.pageOf(week = 20, maxWeek = 20))
        assertEquals(19, WeekPagerIndex.pageOf(week = 99, maxWeek = 20))
    }

    @Test
    fun weekOf_neverReturnsZero() {
        assertEquals(1, WeekPagerIndex.weekOf(page = -1, maxWeek = 20))
        assertEquals(1, WeekPagerIndex.weekOf(page = 0, maxWeek = 20))
        assertEquals(20, WeekPagerIndex.weekOf(page = 19, maxWeek = 20))
        assertEquals(20, WeekPagerIndex.weekOf(page = 40, maxWeek = 20))
    }
}
