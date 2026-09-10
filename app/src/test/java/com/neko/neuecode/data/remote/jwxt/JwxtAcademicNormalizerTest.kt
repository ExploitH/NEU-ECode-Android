package com.neko.neuecode.data.remote.jwxt

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtAcademicNormalizerTest {

    @Test
    fun normalizeScores_mapsHomeappStudentRowsAndDropsSecrets() {
        val rows = JsonParser.parseString(
            """
            [
              {
                "courseName": "线性代数",
                "courseNo": "A1001",
                "courseType": "必修",
                "credit": "3.0",
                "creditHours": "48",
                "passStatus": "及格",
                "score": "87",
                "secretVal": "must-not-survive"
              },
              {
                "courseName": "大学英语",
                "courseNo": "A1002",
                "courseType": "必修",
                "credit": 2,
                "creditHours": 32,
                "passStatus": "及格",
                "score": "91"
              }
            ]
            """.trimIndent()
        ).asJsonArray

        val document = JwxtAcademicNormalizer.normalizeScores(
            account = "20240001",
            termCode = "2025-2026-2",
            termName = "2025-2026学年春季学期",
            rows = rows,
            generatedAt = "2026-09-10T02:00:00Z",
        )

        assertEquals("2025-2026-2", document.term.code)
        assertEquals(2, document.scores.size)
        assertEquals("线性代数", document.scores[0].courseName)
        assertEquals("A1001", document.scores[0].courseNo)
        assertEquals("必修", document.scores[0].courseType)
        assertEquals(3.0, document.scores[0].credit!!, 0.001)
        assertEquals("87", document.scores[0].score)
        assertEquals("及格", document.scores[0].passStatus)
        assertEquals(2, document.summary.count)
        val snapshot = document.toString()
        assertTrue(snapshot.contains("线性代数"))
        assertTrue(!snapshot.contains("must-not-survive"))
        assertTrue(!snapshot.contains("secretVal"))
    }

    @Test
    fun normalizeScores_emptyDatasIsEmptyTermNotAuthFailure() {
        val document = JwxtAcademicNormalizer.normalizeScores(
            account = "20240001",
            termCode = "2026-2027-1",
            termName = "2026-2027学年秋季学期",
            rows = JsonParser.parseString("[]").asJsonArray,
            generatedAt = "2026-09-10T02:00:00Z",
        )
        assertEquals(0, document.scores.size)
        assertEquals(0, document.summary.count)
    }

    @Test
    fun normalizeExams_mapsHomeappStudentRows() {
        val rows = JsonParser.parseString(
            """
            [
              {
                "courseName": "线性代数",
                "courseNo": "A1001",
                "courseDesc": "期末",
                "examDate": "2026-06-18",
                "examPlace": "信息A101",
                "examSeatNo": "12",
                "examStatus": "已安排",
                "examTimeDescription": "08:30-10:30",
                "examType": "期末考试",
                "examTypeCode": "01",
                "startTime": "08:30",
                "endTime": "10:30",
                "week": 18,
                "teachers": "张三",
                "taskId": "T1",
                "teachingClassId": "JX001",
                "secretVal": "must-not-survive"
              }
            ]
            """.trimIndent()
        ).asJsonArray

        val document = JwxtAcademicNormalizer.normalizeExams(
            account = "20240001",
            termCode = "2025-2026-2",
            termName = "2025-2026学年春季学期",
            rows = rows,
            generatedAt = "2026-09-10T02:00:00Z",
        )

        assertEquals(1, document.exams.size)
        val exam = document.exams[0]
        assertEquals("线性代数", exam.courseName)
        assertEquals("信息A101", exam.place)
        assertEquals("12", exam.seatNo)
        assertEquals("2026-06-18", exam.date)
        assertEquals("08:30", exam.startTime)
        assertEquals("10:30", exam.endTime)
        assertEquals(18, exam.week)
        assertEquals("期末考试", exam.examType)
        assertEquals(listOf("张三"), exam.teachers)
        val snapshot = document.toString()
        assertTrue(!snapshot.contains("must-not-survive"))
        assertTrue(!snapshot.contains("secretVal"))
    }

    @Test
    fun normalizeExams_emptyDatasIsEmptyTerm() {
        val document = JwxtAcademicNormalizer.normalizeExams(
            account = "20240001",
            termCode = "2026-2027-1",
            termName = "2026-2027学年秋季学期",
            rows = JsonParser.parseString("[]").asJsonArray,
            generatedAt = "2026-09-10T02:00:00Z",
        )
        assertEquals(0, document.exams.size)
    }
}
