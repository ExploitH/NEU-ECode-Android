package com.neko.neuecode.widget

import com.google.gson.JsonParser
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWidgetPresentationTest {

    private val document = JwxtScheduleNormalizer.normalize(
        account = "20240001",
        termCode = "2025-2026-2",
        termName = "春",
        campusCode = "01",
        campusName = "南湖",
        sections = emptyList(),
        schedule = JsonParser.parseString(
            """
            {
              "arrangedList": [
                {
                  "courseCode": "A1001",
                  "courseName": "运筹学",
                  "teachClassId": "JX001",
                  "courseSerialNo": "JX001",
                  "teachClassName": "信息2401",
                  "teachingTarget": "信息2401",
                  "credit": "3.0",
                  "campusName": "南湖",
                  "weeksAndTeachers": "2周[理论]/张川[主讲]",
                  "beginTime": "10:30",
                  "endTime": "12:10",
                  "beginSection": 3,
                  "endSection": 4,
                  "placeName": "1号B206",
                  "dayOfWeek": 4,
                  "titleDetail": ["运筹学"],
                  "titleWeekTeacherClassroomDetail": ["2周 张川 1号B206"]
                }
              ],
              "notArrangeList": [],
              "practiceList": []
            }
            """.trimIndent(),
        ).asJsonObject,
        generatedAt = "2026-08-27T00:00:00Z",
    )

    @Test
    fun todayLines_emptyBeforeTermStart() {
        val lines = ScheduleWidgetPresentation.todayLines(
            document = document,
            actualWeek = null,
            todayWeekday = 4,
        )
        assertTrue(lines.first().contains("尚未开始") || lines.first().contains("开学"))
    }

    @Test
    fun todayLines_usesActualWeekNotWeekOneFallback() {
        val lines = ScheduleWidgetPresentation.todayLines(
            document = document,
            actualWeek = 2,
            todayWeekday = 4,
            nowMinutes = minutes("09:00"),
        )
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("运筹学"))
    }

    @Test
    fun todayLines_hidesFinishedClassesAndKeepsCurrentAndUpcoming() {
        val lines = ScheduleWidgetPresentation.todayLines(
            document = threeClassThursday(),
            actualWeek = 2,
            todayWeekday = 4,
            nowMinutes = minutes("10:00"),
        )
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("正在上的课"))
        assertTrue(lines[1].contains("将要上的课"))
        assertTrue(lines.none { it.contains("已上完的课") })
    }

    @Test
    fun todayLines_showsNoClassCopyWhenDayHasNoEvents() {
        val lines = ScheduleWidgetPresentation.todayLines(
            document = document,
            actualWeek = 2,
            todayWeekday = 1,
            nowMinutes = minutes("12:00"),
        )
        assertEquals(listOf("今日无课"), lines)
    }

    @Test
    fun todayLines_showsFinishedCopyAfterLastClassEnds() {
        val lines = ScheduleWidgetPresentation.todayLines(
            document = document,
            actualWeek = 2,
            todayWeekday = 4,
            nowMinutes = minutes("12:10"),
        )
        assertEquals(listOf("今日课程已上完"), lines)
    }

    @Test
    fun nextRefreshMinutes_isTheSoonestRemainingBoundary() {
        val next = ScheduleWidgetPresentation.nextRefreshMinutes(
            items = listOf(
                todayItem("已上完的课", "08:00", "09:40"),
                todayItem("正在上的课", "10:00", "11:40"),
                todayItem("将要上的课", "14:00", "15:40"),
            ),
            nowMinutes = minutes("10:30"),
        )
        assertEquals(minutes("11:40"), next)
    }

    private fun minutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun todayItem(name: String, start: String, end: String) = com.neko.neuecode.domain.jwxt.ScheduleTodayItem(
        eventId = name,
        courseName = name,
        classroom = "A101",
        teachers = emptyList(),
        startTime = start,
        endTime = end,
        startSection = 1,
        endSection = 2,
    )

    private fun threeClassThursday() = JwxtScheduleNormalizer.normalize(
        account = "20240001",
        termCode = "2025-2026-2",
        termName = "春",
        campusCode = "01",
        campusName = "南湖",
        sections = emptyList(),
        schedule = JsonParser.parseString(
            """
            {
              "arrangedList": [
                ${arranged("已上完的课", "08:00", "09:40", 1, 2)},
                ${arranged("正在上的课", "10:00", "11:40", 3, 4)},
                ${arranged("将要上的课", "14:00", "15:40", 5, 6)}
              ],
              "notArrangeList": [],
              "practiceList": []
            }
            """.trimIndent(),
        ).asJsonObject,
        generatedAt = "2026-08-27T00:00:00Z",
    )

    private fun arranged(
        name: String,
        begin: String,
        end: String,
        beginSection: Int,
        endSection: Int,
    ): String {
        return """
            {
              "courseCode": "$name",
              "courseName": "$name",
              "teachClassId": "$name",
              "courseSerialNo": "$name",
              "teachClassName": "信息2401",
              "teachingTarget": "信息2401",
              "credit": "3.0",
              "campusName": "南湖",
              "weeksAndTeachers": "2周[理论]/张川[主讲]",
              "beginTime": "$begin",
              "endTime": "$end",
              "beginSection": $beginSection,
              "endSection": $endSection,
              "placeName": "1号B206",
              "dayOfWeek": 4,
              "titleDetail": ["$name"],
              "titleWeekTeacherClassroomDetail": ["2周 张川 1号B206"]
            }
        """.trimIndent()
    }

    @Test
    fun dayCards_showEveryClassOnThatDayEvenIfFinished() {
        val cards = ScheduleWidgetPresentation.dayCards(
            document = threeClassThursday(),
            week = 2,
            weekday = 4,
        )
        assertEquals(3, cards.size)
        assertEquals(listOf("已上完的课", "正在上的课", "将要上的课"), cards.map { it.courseName })
        assertEquals("08:00-09:40", cards.first().timeLabel)
        assertTrue(cards.first().backgroundColor != 0)
        assertEquals("1号B206", cards.first().classroom)
    }

    @Test
    fun todaySubtitle_includesCalendarDate() {
        assertEquals(
            "第2周 · 8月27日 周四",
            ScheduleWidgetPresentation.todaySubtitle(
                actualWeek = 2,
                todayWeekday = 4,
                epochDay = 20_692L,
            ),
        )
        assertEquals(
            "开学日前 · 8月27日 周四",
            ScheduleWidgetPresentation.todaySubtitle(
                actualWeek = null,
                todayWeekday = 4,
                epochDay = 20_692L,
            ),
        )
    }

    @Test
    fun todayCards_hideFinishedClassesAndKeepCurrentAndUpcoming() {
        val cards = ScheduleWidgetPresentation.todayCards(
            document = threeClassThursday(),
            actualWeek = 2,
            todayWeekday = 4,
            nowMinutes = minutes("10:00"),
        )
        assertEquals(2, cards.size)
        assertEquals(listOf("正在上的课", "将要上的课"), cards.map { it.courseName })
        assertEquals("10:00-11:40", cards.first().timeLabel)
        assertTrue(cards.first().backgroundColor != 0)
    }

    @Test
    fun todayCards_emptyWhenFinished() {
        val cards = ScheduleWidgetPresentation.todayCards(
            document = document,
            actualWeek = 2,
            todayWeekday = 4,
            nowMinutes = minutes("12:10"),
        )
        assertTrue(cards.isEmpty())
    }

    @Test
    fun dayCards_emptyCopyWhenThatWeekdayHasNoEvents() {
        val cards = ScheduleWidgetPresentation.dayCards(
            document = document,
            week = 2,
            weekday = 1,
        )
        assertTrue(cards.isEmpty())
        assertEquals("当天无课", ScheduleWidgetPresentation.dayEmptyCopy)
    }
}
