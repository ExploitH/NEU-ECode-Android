package com.neko.neuecode.ui.notice

import com.neko.neuecode.ui.navigation.MainDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeBadgePlacementTest {

    @Test
    fun bellBadge_usesOverflowPaddingSoDotIsNotClipped() {
        assertTrue(
            "IconButton clips overflow; the bell needs extra padding around the icon",
            NoticeBadgeStyle.bellContentPaddingDp > 0,
        )
        assertTrue(
            "The red dot itself has to be a complete circle, not a teardrop remnant",
            NoticeBadgeStyle.dotSizeDp >= 8,
        )
        assertFalse(
            "Do not wrap BadgedBox in a clipping IconButton surface",
            NoticeBadgeStyle.clipBellToIconButton,
        )
    }

    @Test
    fun meTab_showsTheSameUnreadDotAsTheBell() {
        assertEquals(MainDestinations.ME, NoticeBadgeStyle.bottomBarBadgeRoute)
        assertEquals(MainDestinations.LABEL_ME, NoticeBadgeStyle.bottomBarBadgeLabel)
        assertTrue(NoticeBadgeStyle.mirrorBellBadgeOnMeTab)
        assertFalse(
            "Low-priority unread still must not light either the bell or 我的",
            NoticeBadgeStyle.includeLowPriorityOnMeTab,
        )
    }
}
