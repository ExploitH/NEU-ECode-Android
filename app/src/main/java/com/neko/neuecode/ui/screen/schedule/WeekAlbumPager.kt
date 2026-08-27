package com.neko.neuecode.ui.screen.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.ScheduleGridCell
import com.neko.neuecode.domain.jwxt.ScheduleTodayHighlight
import kotlinx.coroutines.flow.distinctUntilChanged

/** Compose current week plus one neighbour on each side. */
const val WEEK_PAGER_PREFETCH = 1

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekAlbumPager(
    document: JwxtScheduleDocument,
    selectedWeek: Int,
    maxWeek: Int,
    actualWeek: Int?,
    todayWeekday: Int,
    pagerState: PagerState,
    bouncePx: Float,
    onWeekSettled: (Int) -> Unit,
    onCellClick: (ScheduleGridCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = maxWeek.coerceAtLeast(1)
    LaunchedEffect(selectedWeek, pages) {
        val target = WeekPagerIndex.pageOf(selectedWeek, pages)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                onWeekSettled(WeekPagerIndex.weekOf(page, pages))
            }
    }
    HorizontalPager(
        state = pagerState,
        beyondBoundsPageCount = WEEK_PAGER_PREFETCH,
        pageSpacing = 8.dp,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        modifier = modifier.graphicsLayer { translationX = bouncePx },
    ) { page ->
        val week = WeekPagerIndex.weekOf(page, pages)
        WeekGridPane(
            document = document,
            week = week,
            todayWeekday = ScheduleTodayHighlight.weekdayToMark(
                selectedWeek = week,
                actualWeek = actualWeek,
                todayWeekday = todayWeekday,
            ),
            onCellClick = onCellClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

suspend fun bounceWeekEdge(bounce: Animatable<Float, *>, towardPrevious: Boolean) {
    val peak = if (towardPrevious) 56f else -56f
    bounce.animateTo(
        peak,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
    )
    bounce.animateTo(
        0f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
    )
}
