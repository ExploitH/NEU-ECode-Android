package com.neko.neuecode.ui.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neko.neuecode.ui.navigation.MainDestinations

/**
 * Shared unread-dot rules for the 我的 bell and the bottom-bar 我的 tab.
 *
 * Material3 [androidx.compose.material3.BadgedBox] inside a default [androidx.compose.material3.IconButton]
 * is clipped by the 40.dp circular minimum-touch-target, which is why the live
 * screenshot showed a teardrop remnant instead of a full circle.
 */
object NoticeBadgeStyle {
    const val bellContentPaddingDp = 6
    const val dotSizeDp = 8
    const val clipBellToIconButton = false
    const val mirrorBellBadgeOnMeTab = true
    const val includeLowPriorityOnMeTab = false
    const val bottomBarBadgeRoute = MainDestinations.ME
    const val bottomBarBadgeLabel = MainDestinations.LABEL_ME
    const val BELL_BADGE_TAG = "notice_bell_badge"
    const val ME_TAB_BADGE_TAG = "notice_me_tab_badge"
}

@Composable
fun UnreadNoticeDot(
    visible: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .size(NoticeBadgeStyle.dotSizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .testTag(testTag)
            .semantics { contentDescription = "未读通知" },
    )
}

@Composable
fun NoticeBellIconSlot(
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(NoticeBadgeStyle.bellContentPaddingDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        UnreadNoticeDot(
            visible = showBadge,
            testTag = NoticeBadgeStyle.BELL_BADGE_TAG,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 1.dp, y = (-1).dp),
        )
    }
}

@Composable
fun NoticeMeTabIconSlot(
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    // NavigationBarItem already has room for a small overlay; extra padding
    // would shrink the 我的 icon relative to 付款码 / 课表.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        icon()
        UnreadNoticeDot(
            visible = showBadge,
            testTag = NoticeBadgeStyle.ME_TAB_BADGE_TAG,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp),
        )
    }
}
