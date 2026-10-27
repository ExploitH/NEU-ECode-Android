package com.neko.neuecode.domain.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeInboxTest {

    private val low = notice("low-1", NoticePriority.LOW, "低优先级")
    private val medium = notice("mid-1", NoticePriority.MEDIUM, "中优先级")
    private val high1 = notice("high-1", NoticePriority.HIGH, "高优先级一", publishedAt = "2026-10-28T10:00:00Z")
    private val high2 = notice("high-2", NoticePriority.HIGH, "高优先级二", publishedAt = "2026-10-28T12:00:00Z")

    @Test
    fun lowUnread_hasNoBadgeAndNoSplash() {
        val notices = listOf(low)
        assertFalse(NoticeInbox.showBadge(notices, emptySet()))
        assertTrue(NoticeInbox.splashNotices(notices, emptySet()).isEmpty())
    }

    @Test
    fun mediumUnread_hasBadgeButNoSplash() {
        val notices = listOf(medium, low)
        assertTrue(NoticeInbox.showBadge(notices, emptySet()))
        assertTrue(NoticeInbox.splashNotices(notices, emptySet()).isEmpty())
    }

    @Test
    fun highUnread_hasBadgeAndSplash() {
        val notices = listOf(high1)
        assertTrue(NoticeInbox.showBadge(notices, emptySet()))
        assertEquals(listOf(high1), NoticeInbox.splashNotices(notices, emptySet()))
    }

    @Test
    fun readMessages_neverBadgeOrSplash() {
        val notices = listOf(low, medium, high1, high2)
        val read = notices.map { it.id }.toSet()
        assertFalse(NoticeInbox.showBadge(notices, read))
        assertTrue(NoticeInbox.splashNotices(notices, read).isEmpty())
    }

    @Test
    fun multipleUnreadHigh_splashNewestFirst() {
        val splash = NoticeInbox.splashNotices(listOf(high1, medium, high2, low), emptySet())
        assertEquals(listOf(high2, high1), splash)
    }

    @Test
    fun openingDetails_marksEveryMessageRead() {
        val notices = listOf(low, medium, high1)
        val read = NoticeInbox.markAllRead(notices, setOf("stale-id"))
        assertEquals(setOf("stale-id", "low-1", "mid-1", "high-1"), read)
        assertFalse(NoticeInbox.showBadge(notices, read))
        assertTrue(NoticeInbox.splashNotices(notices, read).isEmpty())
    }

    @Test
    fun markingOneHighRead_keepsOtherHighSplash() {
        val notices = listOf(high1, high2, medium)
        val read = NoticeInbox.markRead(notices, emptySet(), "high-2")
        assertEquals(setOf("high-2"), read)
        assertTrue(NoticeInbox.showBadge(notices, read))
        assertEquals(listOf(high1), NoticeInbox.splashNotices(notices, read))
    }

    private fun notice(
        id: String,
        priority: NoticePriority,
        title: String,
        publishedAt: String = "2026-10-28T08:00:00Z",
    ): AppNotice {
        return AppNotice(
            id = id,
            title = title,
            body = "${title}正文",
            priority = priority,
            publishedAt = publishedAt,
        )
    }
}
