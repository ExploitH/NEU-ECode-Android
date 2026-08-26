package com.neko.neuecode.data.remote.jwxt

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtScheduleNormalizerTest {

    @Test
    fun expandWeeks_expandsRangesAndSingleWeeks() {
        assertEquals(listOf(1, 2, 3, 5, 6), JwxtScheduleNormalizer.expandWeeks("1-3周,5-6周"))
    }

    @Test
    fun expandWeeks_skipsEvenWeeksWhenOddOnly() {
        assertEquals(listOf(1, 3, 5), JwxtScheduleNormalizer.expandWeeks("1-5单周"))
    }

    @Test
    fun normalize_groupsSameCourseAndKeepsSecretValOutOfDomain() {
        val raw = JsonParser.parseString(
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
        ).asJsonObject

        val result = JwxtScheduleNormalizer.normalize(
            account = "20240001",
            termCode = "2026-2027-1",
            termName = "2026-2027学年秋季学期",
            campusCode = "01",
            campusName = "浑南校区",
            sections = emptyList(),
            schedule = raw,
            generatedAt = "2026-08-26T00:00:00Z"
        )

        assertEquals(1, result.summary.courseCount)
        assertEquals(2, result.summary.eventCount)
        assertEquals(listOf(1, 2, 3, 5), result.events[0].weeks)
        assertEquals(listOf("张三", "李四"), result.events[0].teachers)
        assertEquals("星期一", result.events[0].weekdayName)
        assertEquals(2, result.courses[0].eventIds.size)
        assertEquals(2.0, result.courses[0].credit!!, 0.0)
        assertTrue(result.events.none { event -> event.courseCode.isBlank() })
        val encoded = result.toDebugSnapshot()
        assertTrue(!encoded.contains("must-not-survive"))
        assertTrue(!encoded.contains("secretVal"))
    }
}
