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
        )
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("运筹学"))
    }
}
