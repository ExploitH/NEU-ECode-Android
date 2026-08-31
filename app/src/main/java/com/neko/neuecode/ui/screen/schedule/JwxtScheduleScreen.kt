/*
 * Schedule chrome (week chip + term menu) adapted from Sleepy
 * https://github.com/lingion/sleepy
 * Copyright (C) Lingion and contributors
 * Licensed under the GNU General Public License v3.0.
 *
 * NEU eCode wires this chrome to JWXT terms (`xnxqcx.do`) instead of
 * Sleepy's local timetable list.
 */
package com.neko.neuecode.ui.screen.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neko.neuecode.data.local.schedule.WeekStartDay
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.ScheduleLoginInitHint
import com.neko.neuecode.ui.components.BrandLoadingMark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JwxtScheduleScreen(
    onOpenIntranet: () -> Unit = {},
    viewModel: JwxtScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val document = state.document
    val colors = MaterialTheme.colorScheme
    val maxWeek = state.maxWeek.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = WeekPagerIndex.pageOf(state.selectedWeek, maxWeek),
        pageCount = { maxWeek },
    )
    val bounce = remember { Animatable(0f, Float.VectorConverter) }
    val scope = rememberCoroutineScope()
    fun goWeek(delta: Int) {
        val next = state.selectedWeek + delta
        when {
            next < 1 -> scope.launch { bounceWeekEdge(bounce, towardPrevious = true) }
            next > maxWeek -> scope.launch { bounceWeekEdge(bounce, towardPrevious = false) }
            else -> {
                viewModel.selectWeek(next)
                scope.launch { pagerState.animateScrollToPage(WeekPagerIndex.pageOf(next, maxWeek)) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { viewModel.openSettings() }) {
                        Text("课表设定")
                    }
                },
                windowInsets = WindowInsets.statusBars,
                actions = {
                    TermPicker(
                        terms = state.terms,
                        selectedCode = state.selectedTermCode ?: document?.term?.code,
                        fallbackName = document?.term?.name ?: "课表",
                        onSelect = { viewModel.selectTerm(it) },
                    )
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (state.showIntranetHint) {
                Button(onClick = onOpenIntranet, modifier = Modifier.fillMaxWidth()) {
                    Text("去内网连接")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (state.loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    if (state.showLoginInitHint) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ScheduleLoginInitHint.TEXT,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            WeekChrome(
                selectedWeek = state.selectedWeek,
                maxWeek = maxWeek,
                onPrev = { goWeek(-1) },
                onNext = { goWeek(1) },
                onSelectWeek = { week ->
                    viewModel.selectWeek(week)
                    scope.launch { pagerState.animateScrollToPage(WeekPagerIndex.pageOf(week, maxWeek)) }
                },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                SegmentedButton(
                    selected = state.pane == SchedulePane.Week,
                    onClick = { viewModel.selectPane(SchedulePane.Week) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("网格") }
                SegmentedButton(
                    selected = state.pane == SchedulePane.Today,
                    onClick = { viewModel.selectPane(SchedulePane.Today) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("今日") }
            }
            if (state.loading && document == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BrandLoadingMark(
                        caption = state.message,
                        footnote = if (state.showLoginInitHint) ScheduleLoginInitHint.TEXT else null,
                    )
                }
            } else if (document == null) {
                Text(
                    text = state.message.ifBlank { "尚未同步课表" },
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                AnimatedContent(
                    targetState = state.pane,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "schedule-pane",
                    modifier = Modifier.weight(1f),
                ) { pane ->
                    when (pane) {
                        SchedulePane.Week -> {
                            WeekAlbumPager(
                                document = document,
                                selectedWeek = state.selectedWeek,
                                maxWeek = maxWeek,
                                actualWeek = state.actualWeek,
                                todayWeekday = state.todayWeekday,
                                weekStartDay = state.weekStartDay,
                                termStartEpochDay = state.termStartEpochDay,
                                pagerState = pagerState,
                                bouncePx = bounce.value,
                                onWeekSettled = { viewModel.selectWeek(it) },
                                onCellClick = { viewModel.openEvent(it.eventId) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        SchedulePane.Today -> {
                            val todayWeek = com.neko.neuecode.domain.jwxt.ScheduleTodayHighlight.todayPaneWeek(
                                actualWeek = state.actualWeek,
                                selectedWeek = state.selectedWeek,
                            )
                            val unavailable = com.neko.neuecode.domain.jwxt.ScheduleTodayCopy.todayUnavailableMessage(
                                termStartEpochDay = state.termStartEpochDay,
                                actualWeek = state.actualWeek,
                            )
                            if (todayWeek == null || unavailable != null) {
                                Text(
                                    text = unavailable ?: com.neko.neuecode.domain.jwxt.ScheduleTodayCopy.MISSING_TERM_START,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            } else {
                                TodayPane(
                                    document = document,
                                    weekday = state.todayWeekday,
                                    week = todayWeek,
                                    onItemClick = { viewModel.openEvent(it.eventId) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedDetail?.let { detail ->
        CourseDetailSheet(detail = detail, onDismiss = { viewModel.dismissDetail() })
    }
    if (state.showSettings) {
        ScheduleSettingsDialog(
            terms = state.terms,
            selectedTermCode = state.selectedTermCode,
            termStartEpochDay = state.termStartEpochDay,
            weekStartDay = state.weekStartDay,
            onDismiss = { viewModel.dismissSettings() },
            onSave = { term, start, weekStart -> viewModel.saveSettings(term, start, weekStart) },
        )
    }
}

@Composable
private fun TermPicker(
    terms: List<JwxtNamedCode>,
    selectedCode: String?,
    fallbackName: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = terms.firstOrNull { it.code == selectedCode }
    val label = selected?.name ?: fallbackName
    Box {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = terms.isNotEmpty()) { open = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            terms.forEach { term ->
                Text(
                    text = term.name.ifBlank { term.code },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            open = false
                            onSelect(term.code)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    fontWeight = if (term.code == selectedCode) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (term.code == selectedCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekChrome(
    selectedWeek: Int,
    maxWeek: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectWeek: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundNav(enabled = true, onClick = onPrev) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一周")
        }
        Box {
            Text(
                text = "第 ${selectedWeek} 周",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.primaryContainer)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.width(280.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "跳到周次",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        (1..maxWeek.coerceAtLeast(1)).forEach { week ->
                            val selected = week == selectedWeek
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) colors.primary else colors.surfaceContainerHigh)
                                    .clickable {
                                        onSelectWeek(week)
                                        menuOpen = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = week.toString(),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) colors.onPrimary else colors.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        RoundNav(enabled = true, onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "下一周")
        }
    }
}

@Composable
private fun RoundNav(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSettingsDialog(
    terms: List<JwxtNamedCode>,
    selectedTermCode: String?,
    termStartEpochDay: Long?,
    weekStartDay: WeekStartDay,
    onDismiss: () -> Unit,
    onSave: (String?, Long?, WeekStartDay) -> Unit,
) {
    var termCode by remember { mutableStateOf(selectedTermCode.orEmpty()) }
    var startDay by remember { mutableStateOf(termStartEpochDay) }
    var weekStart by remember { mutableStateOf(weekStartDay) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateLabel = startDay?.let { formatEpochDay(it) } ?: "未设置"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课表设定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("教务学年学期列表不含开学日（QSSYRQ 为空），学期开始日期需本地填写。")
                Text("每周第一天", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = weekStart == WeekStartDay.SUNDAY,
                        onClick = { weekStart = WeekStartDay.SUNDAY },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("周日") }
                    SegmentedButton(
                        selected = weekStart == WeekStartDay.MONDAY,
                        onClick = { weekStart = WeekStartDay.MONDAY },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("周一") }
                }
                Text("默认学期", style = MaterialTheme.typography.labelMedium)
                if (terms.isEmpty()) {
                    OutlinedTextField(
                        value = termCode,
                        onValueChange = { termCode = it },
                        label = { Text("学期代码，如 2025-2026-2") },
                        singleLine = true,
                    )
                } else {
                    terms.forEach { term ->
                        val selected = term.code == termCode
                        Text(
                            text = term.name.ifBlank { term.code },
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { termCode = term.code }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
                Text("学期开始日期：$dateLabel", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showDatePicker = true }) { Text("选择日期") }
                if (startDay != null) {
                    TextButton(onClick = { startDay = null }) { Text("清除开学日") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(termCode.ifBlank { null }, startDay, weekStart) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDay?.let { it * 86_400_000L },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startDay = pickerState.selectedDateMillis?.let { it / 86_400_000L }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun formatEpochDay(epochDay: Long): String {
    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    calendar.timeInMillis = epochDay * 86_400_000L
    val y = calendar.get(java.util.Calendar.YEAR)
    val m = calendar.get(java.util.Calendar.MONTH) + 1
    val d = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(y, m, d)
}
