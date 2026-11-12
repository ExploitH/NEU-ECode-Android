package com.neko.neuecode.domain.jwxt

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExamWidgetVisibilityTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")
    private val exam = JwxtExam(
        courseNo = "A", courseName = "考试", courseDesc = "", date = "2026-11-05", place = "",
        seatNo = "", status = "", timeDescription = "", examType = "", examTypeCode = "",
        startTime = "08:30", endTime = "10:30", week = null, teachers = emptyList(),
        taskId = "", teachingClassId = "",
    )

    @Test fun keepsUpcomingAndHidesMoreThanSevenDaysAfterEnd() {
        val end = instant(2026, 11, 5, 10, 30)
        assertTrue(ExamWidgetVisibility.visible(exam, end - 1, zone))
        assertTrue(ExamWidgetVisibility.visible(exam, end + 7L * 86_400_000L, zone))
        assertFalse(ExamWidgetVisibility.visible(exam, end + 7L * 86_400_000L + 1, zone))
    }

    @Test fun usesDescriptionEndWhenEndTimeBlank() {
        val described = exam.copy(endTime = "", timeDescription = "13:50-15:50 星期三第2场")
        val end = instant(2026, 11, 5, 15, 50)
        assertTrue(ExamWidgetVisibility.visible(described, end, zone))
        assertFalse(ExamWidgetVisibility.visible(described, end + 7L * 86_400_000L + 1, zone))
    }

    @Test fun dateOnlyRetainsSevenDaysAfterThatDay() {
        val dateOnly = exam.copy(endTime = "", startTime = "", timeDescription = "待定")
        val endOfDay = instant(2026, 11, 6, 0, 0)
        assertTrue(ExamWidgetVisibility.visible(dateOnly, endOfDay + 7L * 86_400_000L, zone))
        assertFalse(ExamWidgetVisibility.visible(dateOnly, endOfDay + 7L * 86_400_000L + 1, zone))
    }

    @Test fun unknownDateRemainsVisible() {
        assertTrue(ExamWidgetVisibility.visible(exam.copy(date = "待安排"), 0, zone))
    }

    @Test fun nextChangeIsEarliestFutureExpiry() {
        val later = exam.copy(date = "2026-11-20", endTime = "12:00")
        val now = instant(2026, 11, 1, 0, 0)
        assertEquals(instant(2026, 11, 5, 10, 30) + 7L * 86_400_000L, ExamWidgetVisibility.nextChangeMillis(listOf(exam, later), now, zone))
        assertNull(ExamWidgetVisibility.nextChangeMillis(listOf(exam.copy(date = "2000-01-01")), now, zone))
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, 0)
        return calendar.timeInMillis
    }
}
