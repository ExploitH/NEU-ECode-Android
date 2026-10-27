package com.neko.neuecode.ui.screen.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neko.neuecode.domain.notice.AppNotice
import com.neko.neuecode.domain.notice.NoticeMarkdown
import com.neko.neuecode.domain.notice.NoticePriority
import com.neko.neuecode.ui.components.EmptyState
import com.neko.neuecode.ui.notice.NoticeRichBody
import com.neko.neuecode.ui.theme.panel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    onBack: () -> Unit,
    viewModel: NoticeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openedNotice by remember { mutableStateOf<AppNotice?>(null) }
    LaunchedEffect(Unit) {
        viewModel.markAllReadOnOpen()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text("通知") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.notices.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.NotificationsNone,
                title = "暂无通知",
                message = "检查更新时会一并同步通知，不必后台常驻拉取。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.notices, key = { it.id }) { notice ->
                    NoticeCard(
                        notice = notice,
                        read = notice.id in state.readIds,
                        onOpen = { openedNotice = notice },
                    )
                }
            }
        }
    }

    openedNotice?.let { notice ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { openedNotice = null },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriorityChip(notice.priority)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    notice.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(12.dp))
                NoticeRichBody(markdown = notice.body)
                if (notice.publishedAt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        notice.publishedAt.replace('T', ' ').removeSuffix("Z"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: AppNotice,
    read: Boolean,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriorityChip(notice.priority)
            if (!read) {
                Text(
                    "未读",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            notice.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            NoticeMarkdown.listHint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primary,
        )
        if (notice.publishedAt.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                notice.publishedAt.replace('T', ' ').removeSuffix("Z"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.outline,
            )
        }
    }
}

@Composable
private fun PriorityChip(priority: NoticePriority) {
    val label = when (priority) {
        NoticePriority.HIGH -> "高"
        NoticePriority.MEDIUM -> "中"
        NoticePriority.LOW -> "低"
    }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onPrimaryContainer,
        )
    }
}
