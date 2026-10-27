package com.neko.neuecode.ui.notice

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neko.neuecode.domain.notice.AppNotice
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HighPriorityNoticeSplash(
    notices: List<AppNotice>,
    onMarkRead: (String) -> Unit,
    onLater: () -> Unit,
) {
    if (notices.isEmpty()) return
    val initialIds = remember(notices) { notices.map { it.id } }
    var remaining by remember(initialIds) { mutableStateOf(notices) }
    if (remaining.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { remaining.size.coerceAtLeast(1) },
    )
    val scope = rememberCoroutineScope()
    val safePage = pagerState.currentPage.coerceIn(0, remaining.lastIndex)
    val current = remaining[safePage]

    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                if (remaining.size > 1) {
                    "重要通知 ${safePage + 1}/${remaining.size}"
                } else {
                    "重要通知"
                },
            )
        },
        text = {
            if (remaining.size == 1) {
                NoticeSplashBody(current)
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    remaining.getOrNull(page)?.let { NoticeSplashBody(it) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = current.id
                    val next = remaining.filterNot { it.id == id }
                    remaining = next
                    onMarkRead(id)
                    if (next.isEmpty()) {
                        onLater()
                    } else {
                        val nextPage = safePage.coerceAtMost(next.lastIndex)
                        scope.launch { pagerState.scrollToPage(nextPage) }
                    }
                },
            ) {
                Text("已读")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("稍后再看")
            }
        },
    )
}

@Composable
private fun NoticeSplashBody(notice: AppNotice) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            notice.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(modifier = Modifier.height(8.dp))
        NoticeRichBody(markdown = notice.body, scrollable = true)
    }
}
