package com.neko.neuecode.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDayPagerPolicyTest {

    @Test
    fun defaultOffset_isToday() {
        assertEquals(0, ScheduleDayPagerPolicy.normalizeOffset(null))
        assertEquals(0, ScheduleDayPagerPolicy.normalizeOffset(0))
    }

    @Test
    fun previousAndNext_moveOneDayAndTodayResets() {
        assertEquals(-1, ScheduleDayPagerPolicy.shift(0, -1))
        assertEquals(1, ScheduleDayPagerPolicy.shift(0, 1))
        assertEquals(0, ScheduleDayPagerPolicy.todayOffset())
        assertEquals(-2, ScheduleDayPagerPolicy.shift(-1, -1))
        assertEquals(2, ScheduleDayPagerPolicy.shift(1, 1))
    }

    @Test
    fun selectedEpochDay_followsOffsetFromToday() {
        val today = 20_000L
        assertEquals(today, ScheduleDayPagerPolicy.selectedEpochDay(today, 0))
        assertEquals(today - 1, ScheduleDayPagerPolicy.selectedEpochDay(today, -1))
        assertEquals(today + 1, ScheduleDayPagerPolicy.selectedEpochDay(today, 1))
    }

    @Test
    fun title_marksTodayAndRelativeDays() {
        assertEquals("今日 · 周四", ScheduleDayPagerPolicy.title(offset = 0, weekday = 4, week = 2))
        assertEquals("昨天 · 周三", ScheduleDayPagerPolicy.title(offset = -1, weekday = 3, week = 2))
        assertEquals("明天 · 周五", ScheduleDayPagerPolicy.title(offset = 1, weekday = 5, week = 2))
        assertEquals("第2周 · 周一", ScheduleDayPagerPolicy.title(offset = 4, weekday = 1, week = 2))
        assertTrue(ScheduleDayPagerPolicy.isToday(0))
        assertFalse(ScheduleDayPagerPolicy.isToday(1))
    }

    @Test
    fun clickActions_areDistinct() {
        assertEquals("com.neko.neuecode.widget.ACTION_DAY_PREV", ScheduleDayPagerPolicy.prevAction)
        assertEquals("com.neko.neuecode.widget.ACTION_DAY_TODAY", ScheduleDayPagerPolicy.todayAction)
        assertEquals("com.neko.neuecode.widget.ACTION_DAY_NEXT", ScheduleDayPagerPolicy.nextAction)
        assertTrue(ScheduleDayPagerPolicy.acceptsPagerAction(ScheduleDayPagerPolicy.prevAction))
        assertTrue(ScheduleDayPagerPolicy.acceptsPagerAction(ScheduleDayPagerPolicy.todayAction))
        assertTrue(ScheduleDayPagerPolicy.acceptsPagerAction(ScheduleDayPagerPolicy.nextAction))
        assertFalse(ScheduleDayPagerPolicy.acceptsPagerAction("android.appwidget.action.APPWIDGET_UPDATE"))
    }
}
