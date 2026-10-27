package com.neko.neuecode.data.remote.update

import com.google.gson.JsonParser
import com.neko.neuecode.domain.notice.NoticePriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNoticeParserTest {

    @Test
    fun parseNotices_readsSnakeCaseAndDropsInvalidRows() {
        val root = JsonParser.parseString(
            """
            {
              "latest_version_code": 76,
              "notices": [
                {
                  "id": "high-1",
                  "title": "高优先级",
                  "body": "开屏提示",
                  "priority": "HIGH",
                  "published_at": "2026-10-28T12:00:00Z"
                },
                {
                  "id": "mid-1",
                  "title": "中优先级",
                  "content": "只显示红点",
                  "priority": "medium"
                },
                {
                  "title": "missing-id",
                  "body": "should drop"
                },
                {
                  "id": "expired-1",
                  "title": "过期",
                  "body": "gone",
                  "priority": "high",
                  "expires_at": "2020-01-01T00:00:00Z"
                }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        val notices = AppNoticeParser.parseNotices(root, nowMillis = 1_730_000_000_000L)
        assertEquals(2, notices.size)
        assertEquals("high-1", notices[0].id)
        assertEquals(NoticePriority.HIGH, notices[0].priority)
        assertEquals("开屏提示", notices[0].body)
        assertEquals("mid-1", notices[1].id)
        assertEquals(NoticePriority.MEDIUM, notices[1].priority)
        assertEquals("只显示红点", notices[1].body)
    }

    @Test
    fun parseNotices_missingFieldIsEmpty() {
        val root = JsonParser.parseString("""{"latest_version_code":76}""").asJsonObject
        assertTrue(AppNoticeParser.parseNotices(root).isEmpty())
    }

    @Test
    fun parseNotices_ignoresUnknownKeysAndKeepsVersionPayloadReadable() {
        val root = JsonParser.parseString(
            """
            {
              "latest_version_code": 76,
              "latest_version_name": "7.6",
              "min_supported_version_code": 60,
              "release_notes": "notes",
              "update_required": false,
              "force_update": false,
              "notices": []
            }
            """.trimIndent(),
        ).asJsonObject
        assertEquals(76, root.get("latest_version_code").asInt)
        assertEquals(false, root.get("update_required").asBoolean)
        assertTrue(AppNoticeParser.parseNotices(root).isEmpty())
    }

    @Test
    fun parseNotices_malformedNoticesFieldDoesNotThrow() {
        val root = JsonParser.parseString(
            """
            {
              "latest_version_code": 76,
              "update_required": false,
              "notices": {"oops": true}
            }
            """.trimIndent(),
        ).asJsonObject
        assertEquals(76, root.get("latest_version_code").asInt)
        assertTrue(AppNoticeParser.parseNotices(root).isEmpty())
    }
}
