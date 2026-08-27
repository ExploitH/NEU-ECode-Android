package com.neko.neuecode.domain.jwxt

import com.google.gson.JsonParser
import com.neko.neuecode.data.remote.jwxt.JwxtScheduleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePresentationTest {

    private val document: JwxtScheduleDocument = JwxtScheduleNormalizer.normalize(
        account = "20240001",
        termCode = "2026-2027-1",
        termName = "2026-2027学年秋季学期",
        campusCode = "01",
        campusName = "浑南校区",
        sections = emptyList(),
        schedule = JsonParser.parseString(
            """
            {
              "arrangedList": [
                {
                  "courseCode": "A1001",
                  "courseName": "测试课程",
                  "teachClassId": "JX001",
                  "courseSerialNo": "JX001",
                  "teachClassName": "信息2401",
                  "teachingTarget": "信息2401",
                  "credit": "2.0",
                  "campusName": "浑南校区",
                  "weeksAndTeachers": "1-3周,5周[理论]/张三[主讲],李四[辅讲]",
                  "beginTime": "08:30",
                  "endTime": "10:10",
                  "beginSection": 1,
                  "endSection": 2,
                  "placeName": "信息A101",
                  "dayOfWeek": 1,
                  "secretVal": "must-not-survive",
                  "titleDetail": ["信息2401", "测试课程 JX001", "1-3周,5周 张三 浑南校区 信息A101", "考试 / 百分制"],
                  "titleWeekTeacherClassroomDetail": ["1-3周,5周 张三 浑南校区 信息A101"]
                },
                {
                  "courseCode": "A1001",
                  "courseName": "测试课程",
                  "teachClassId": "JX001",
                  "courseSerialNo": "JX001",
                  "teachClassName": "信息2401",
                  "teachingTarget": "信息2401",
                  "credit": "2.0",
                  "campusName": "浑南校区",
                  "weeksAndTeachers": "1-3周,5周[理论]/张三[主讲],李四[辅讲]",
                  "beginTime": "10:30",
                  "endTime": "12:10",
                  "beginSection": 3,
                  "endSection": 4,
                  "placeName": "信息A101",
                  "dayOfWeek": 3,
                  "secretVal": "must-not-survive",
                  "titleDetail": ["信息2401", "测试课程 JX001", "1-3周,5周 张三 浑南校区 信息A101", "考试 / 百分制"],
                  "titleWeekTeacherClassroomDetail": ["1-3周,5周 张三 浑南校区 信息A101"]
                }
              ],
              "notArrangeList": [],
              "practiceList": []
            }
            """.trimIndent()
        ).asJsonObject,
        generatedAt = "2026-08-26T00:00:00Z"
    )

    @Test
    fun cellsForWeek_includesEventsOnlyWhenWeekIsInEventWeeks() {
        val week1 = SchedulePresentation.cellsForWeek(document, week = 1)
        val week4 = SchedulePresentation.cellsForWeek(document, week = 4)
        val week5 = SchedulePresentation.cellsForWeek(document, week = 5)

        assertEquals(2, week1.size)
        assertEquals(0, week4.size)
        assertEquals(2, week5.size)

        assertEquals(listOf(1, 3), week1.map { it.weekday })
        assertEquals(listOf(1, 3), week1.map { it.startSection })
        assertEquals(listOf("测试课程", "测试课程"), week1.map { it.courseName })
        assertTrue(week1.all { it.classroom == "信息A101" })
        assertTrue(week1.all { it.courseKey == "A1001:JX001" })
        assertEquals(document.events.map { it.id }, week1.map { it.eventId })
        assertEquals(week1.map { it.eventId }, week5.map { it.eventId })
    }

    @Test
    fun cellsByWeek_matchesPerWeekLookup() {
        val byWeek = SchedulePresentation.cellsByWeek(document, maxWeek = 5)
        assertEquals(5, byWeek.size)
        for (week in 1..5) {
            assertEquals(SchedulePresentation.cellsForWeek(document, week), byWeek[week - 1])
        }
    }

    @Test
    fun todayItems_filtersByWeekdayAndWeek() {
        val mondayWeek1 = SchedulePresentation.todayItems(document, weekday = 1, week = 1)
        val mondayWeek4 = SchedulePresentation.todayItems(document, weekday = 1, week = 4)
        val wednesdayWeek1 = SchedulePresentation.todayItems(document, weekday = 3, week = 1)

        assertEquals(1, mondayWeek1.size)
        assertEquals(document.events[0].id, mondayWeek1[0].eventId)
        assertEquals("测试课程", mondayWeek1[0].courseName)
        assertEquals("信息A101", mondayWeek1[0].classroom)
        assertEquals(listOf("张三", "李四"), mondayWeek1[0].teachers)
        assertEquals("08:30", mondayWeek1[0].startTime)
        assertEquals("10:10", mondayWeek1[0].endTime)
        assertEquals(1, mondayWeek1[0].startSection)
        assertEquals(2, mondayWeek1[0].endSection)

        assertTrue(mondayWeek4.isEmpty())
        assertEquals(1, wednesdayWeek1.size)
        assertEquals(document.events[1].id, wednesdayWeek1[0].eventId)
        assertEquals(3, wednesdayWeek1[0].startSection)
    }

    @Test
    fun detail_exposesReadOnlyFieldsWithoutSecrets() {
        val event = document.events[0]
        val detail = SchedulePresentation.detail(event)

        assertEquals("测试课程", detail.courseName)
        assertEquals(listOf("张三", "李四"), detail.teachers)
        assertEquals("信息A101", detail.classroom)
        assertEquals("1-3周,5周", detail.weekSpec)
        assertEquals("第1-2节", detail.sectionsLabel)
        assertEquals("08:30-10:10", detail.timeLabel)
        assertEquals(2.0, detail.credit!!, 0.0)
        assertEquals("考试", detail.assessment)
        assertEquals("星期一", detail.weekdayName)

        val encoded = detail.toString()
        assertTrue(!encoded.contains("secretVal"))
        assertTrue(!encoded.contains("must-not-survive"))
        assertTrue(!document.toDebugSnapshot().contains("secretVal"))
        assertTrue(!document.toDebugSnapshot().contains("must-not-survive"))
    }
}
