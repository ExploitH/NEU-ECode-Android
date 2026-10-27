package com.neko.neuecode.widget

import com.google.gson.JsonParser
import com.neko.neuecode.data.remote.jwxt.JwxtAcademicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamWidgetPresentationTest {

    private val document = JwxtAcademicNormalizer.normalizeExams(
        account = "20240001",
        termCode = "2025-2026-2",
        termName = "2025-2026学年春季学期",
        rows = JsonParser.parseString(
            """
            [
              {
                "courseName": "大学英语",
                "courseNo": "A1002",
                "examDate": "2026-06-20",
                "examPlace": "信息A102",
                "examSeatNo": "08",
                "examTimeDescription": "14:00-16:00",
                "startTime": "14:00",
                "endTime": "16:00",
                "examType": "期末考试",
                "week": 18
              },
              {
                "courseName": "线性代数",
                "courseNo": "A1001",
                "examDate": "2026-06-18",
                "examPlace": "信息A101",
                "examSeatNo": "12",
                "examTimeDescription": "08:30-10:30",
                "startTime": "08:30",
                "endTime": "10:30",
                "examType": "期末考试",
                "week": 18
              }
            ]
            """.trimIndent(),
        ).asJsonArray,
        generatedAt = "2026-09-10T12:00:00Z",
    )

    @Test
    fun emptyCopy_noCacheAsksToOpenExams() {
        assertEquals("打开考试同步", ExamWidgetPresentation.emptyCopy(null))
        assertEquals("暂无考试缓存", ExamWidgetPresentation.title(null))
    }

    @Test
    fun emptyCopy_emptyTermUsesRequestedCopy() {
        val empty = JwxtAcademicNormalizer.normalizeExams(
            account = "20240001",
            termCode = "2026-2027-1",
            termName = "2026-2027学年秋季学期",
            rows = JsonParser.parseString("[]").asJsonArray,
            generatedAt = "2026-09-10T12:00:00Z",
        )
        assertEquals("本学期暂无考试安排", ExamWidgetPresentation.emptyCopy(empty))
        assertEquals("2026-2027学年秋季学期", ExamWidgetPresentation.title(empty))
        assertTrue(ExamWidgetPresentation.cards(empty).isEmpty())
    }

    @Test
    fun title_usesChineseSeasonNameWhenCacheStoredTermCode() {
        val coded = JwxtAcademicNormalizer.normalizeExams(
            account = "20240001",
            termCode = "2026-2027-1",
            termName = "2026-2027-1",
            rows = JsonParser.parseString("[]").asJsonArray,
            generatedAt = "2026-09-10T12:00:00Z",
        )
        assertEquals("2026-2027学年秋季学期", ExamWidgetPresentation.title(coded))
    }

    @Test
    fun cards_useLatestCachedTermAndSortByDate() {
        val cards = ExamWidgetPresentation.cards(document)
        assertEquals(2, cards.size)
        assertEquals("线性代数", cards[0].courseName)
        assertEquals("2026-06-18", cards[0].dateLine)
        assertEquals("08:30-10:30", cards[0].timeLine)
        assertEquals("信息A101 · 座位 12", cards[0].placeLine)
        assertEquals("大学英语", cards[1].courseName)
        assertEquals("", ExamWidgetPresentation.emptyCopy(document))
        assertEquals("2025-2026学年春季学期", ExamWidgetPresentation.title(document))
    }

    @Test
    fun cards_stripMidnightTimestampAndSplitSessionFromClock() {
        val dirty = JwxtAcademicNormalizer.normalizeExams(
            account = "20240001",
            termCode = "2026-2027-1",
            termName = "2026-2027学年秋季学期",
            rows = JsonParser.parseString(
                """
                [
                  {
                    "courseName": "决策模型与优化",
                    "courseNo": "B2001",
                    "examDate": "2026-11-04 00:00:00",
                    "examPlace": "浑南校区综合楼A101",
                    "examSeatNo": "05",
                    "examTimeDescription": "13:50-15:50  星期三第2场",
                    "startTime": "13:50",
                    "endTime": "15:50",
                    "examType": "期末考试"
                  }
                ]
                """.trimIndent(),
            ).asJsonArray,
            generatedAt = "2026-10-28T12:00:00Z",
        )
        val card = ExamWidgetPresentation.cards(dirty).single()
        assertEquals("决策模型与优化", card.courseName)
        assertEquals("2026-11-04", card.dateLine)
        assertEquals("13:50-15:50 · 星期三第2场", card.timeLine)
        assertEquals("浑南校区综合楼A101 · 座位 05", card.placeLine)
        assertTrue(!card.dateLine.contains("00:00:00"))
        assertTrue(!card.timeLine.contains("00:00:00"))
        assertTrue(!card.placeLine.contains("00:00:00"))
    }
}
