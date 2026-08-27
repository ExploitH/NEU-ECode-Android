package com.neko.neuecode.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwxtScheduleScreen(
    onOpenIntranet: () -> Unit = {},
    viewModel: JwxtScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val document = state.document

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        document?.let { "${it.term.name} · ${it.campus.name}" } ?: "课表",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                windowInsets = WindowInsets.statusBars,
                actions = {
                    TextButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading,
                    ) {
                        Text(if (state.loading) "同步中" else "同步")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.showIntranetHint) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onOpenIntranet, modifier = Modifier.fillMaxWidth()) {
                    Text("去内网连接")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { viewModel.shiftWeek(-1) }) { Text("‹") }
                Text("第 ${state.selectedWeek} 周", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.shiftWeek(1) }) { Text("›") }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.pane == SchedulePane.Week,
                    onClick = { viewModel.selectPane(SchedulePane.Week) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("本周网格") }
                SegmentedButton(
                    selected = state.pane == SchedulePane.Today,
                    onClick = { viewModel.selectPane(SchedulePane.Today) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("今日") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (state.loading) {
                CircularProgressIndicator()
            }
            if (document == null && !state.loading) {
                Text("尚未同步课表", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (document != null) {
                when (state.pane) {
                    SchedulePane.Week -> {
                        val weekCells = com.neko.neuecode.domain.jwxt.SchedulePresentation.cellsForWeek(
                            document,
                            state.selectedWeek,
                        )
                        if (weekCells.isEmpty()) {
                            Text("本周没有课", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        WeekGridPane(
                            document = document,
                            week = state.selectedWeek,
                            todayWeekday = state.todayWeekday,
                            onCellClick = { viewModel.openEvent(it.eventId) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SchedulePane.Today -> TodayPane(
                        document = document,
                        weekday = state.todayWeekday,
                        week = state.selectedWeek,
                        onItemClick = { viewModel.openEvent(it.eventId) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    state.selectedDetail?.let { detail ->
        CourseDetailSheet(detail = detail, onDismiss = { viewModel.dismissDetail() })
    }
}
