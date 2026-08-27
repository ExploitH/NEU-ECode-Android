package com.neko.neuecode.domain.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSyncProgressTest {

    @Test
    fun line_includesStepAndWhatTheUserIsWaitingFor() {
        assertEquals("1/7 正在检测校园网…", ScheduleSyncProgress.probing().line)
        assertEquals("2/7 正在登录教务…", ScheduleSyncProgress.loggingIn().line)
        assertEquals("3/7 正在查询当前学期…", ScheduleSyncProgress.currentTerm().line)
        assertEquals("4/7 正在查询上课校区…", ScheduleSyncProgress.campuses().line)
        assertEquals("5/7 正在获取上课节次…", ScheduleSyncProgress.sections().line)
        assertEquals("6/7 正在下载课程明细…", ScheduleSyncProgress.details().line)
        assertEquals("7/7 正在整理课表…", ScheduleSyncProgress.arranging().line)
    }

    @Test
    fun steps_coverTheMeasuredNetworkBottleneck() {
        val labels = listOf(
            ScheduleSyncProgress.loggingIn().label,
            ScheduleSyncProgress.details().label,
        )
        assertTrue(labels.all { it.contains("正在") })
        assertEquals(7, ScheduleSyncProgress.TOTAL)
    }
}
