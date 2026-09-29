package com.neko.neuecode.ui.screen.academic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neko.neuecode.domain.jwxt.AcademicKind
import com.neko.neuecode.domain.jwxt.JwxtExam
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScore
import com.neko.neuecode.domain.jwxt.ScheduleLoginInitHint
import com.neko.neuecode.domain.jwxt.displayName
import com.neko.neuecode.ui.components.BrandLoadingMark
import com.neko.neuecode.ui.components.EmptyState
import com.neko.neuecode.ui.components.InfoBanner
import com.neko.neuecode.ui.theme.panel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwxtAcademicScreen(
    onBack: () -> Unit,
    onOpenIntranet: () -> Unit = {},
    viewModel: JwxtAcademicViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val hasDocument = when (state.kind) {
        AcademicKind.Scores -> state.scores != null
        AcademicKind.Exams -> state.exams != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.kind.title) },
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    AcademicTermPicker(
                        terms = state.terms,
                        selectedCode = state.selectedTermCode
                            ?: state.scores?.term?.code
                            ?: state.exams?.term?.code,
                        fallbackName = state.scores?.term?.displayName
                            ?: state.exams?.term?.displayName
                            ?: state.kind.title,
                        onSelect = { viewModel.selectTerm(it) },
                    )
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading,
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = "同步")
                        }
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
                InfoBanner(
                    icon = Icons.Outlined.VpnLock,
                    message = "教务接口需要校园内网",
                    actionLabel = "去连接",
                    onAction = onOpenIntranet,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            when {
                state.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BrandLoadingMark(
                            caption = state.message,
                            footnote = if (state.showLoginInitHint) ScheduleLoginInitHint.TEXT else null,
                        )
                    }
                }
                !hasDocument -> {
                    EmptyState(
                        icon = if (state.kind == AcademicKind.Scores) Icons.Outlined.Grade else Icons.Outlined.Event,
                        title = "尚未同步${state.kind.title}",
                        message = state.message.takeIf { it.isNotBlank() },
                        action = {
                            Button(onClick = { viewModel.refresh() }) { Text("立即同步") }
                        },
                    )
                }
                state.kind == AcademicKind.Scores -> {
                    val document = state.scores!!
                    if (document.scores.isEmpty()) {
                        EmptyState(icon = Icons.Outlined.Grade, title = "本学期暂无可查询成绩")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            item {
                                Text(
                                    text = "${document.term.displayName} · ${document.summary.count} 门",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                )
                            }
                            items(document.scores, key = { "${it.courseNo}-${it.courseName}" }) { score ->
                                ScoreCard(score = score, onClick = {
                                    viewModel.openScore(score.courseNo, score.courseName)
                                })
                            }
                        }
                    }
                }
                else -> {
                    val document = state.exams!!
                    if (document.exams.isEmpty()) {
                        EmptyState(icon = Icons.Outlined.Event, title = "本学期暂无可查询考试")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            item {
                                Text(
                                    text = "${document.term.displayName} · ${document.summary.count} 场",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                )
                            }
                            items(
                                document.exams,
                                key = { "${it.courseNo}-${it.date}-${it.place}-${it.seatNo}" },
                            ) { exam ->
                                ExamCard(exam = exam, onClick = {
                                    viewModel.openExam(exam.courseNo, exam.date, exam.place)
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedScore?.let { score ->
        ScoreDetailSheet(score = score, onDismiss = { viewModel.dismissDetail() })
    }
    state.selectedExam?.let { exam ->
        ExamDetailSheet(exam = exam, onDismiss = { viewModel.dismissDetail() })
    }
}

@Composable
private fun ScoreCard(score: JwxtScore, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val failed = isFailing(score)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                score.courseName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    score.courseType.takeIf { it.isNotBlank() },
                    score.credit?.let { "$it 学分" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = score.score.ifBlank { "—" },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (failed) colors.error else colors.primary,
            )
            if (score.passStatus.isNotBlank()) {
                Text(
                    text = score.passStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                )
            }
        }
    }
}

private fun isFailing(score: JwxtScore): Boolean {
    val numeric = score.score.trim().toDoubleOrNull()
    if (numeric != null) return numeric < 60.0
    return score.passStatus.contains("不") || score.score.contains("不及格")
}

@Composable
private fun ExamCard(exam: JwxtExam, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val time = exam.timeDescription.ifBlank {
        listOf(exam.startTime, exam.endTime).filter { it.isNotBlank() }.joinToString("-")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        val whenLabel = listOf(exam.date, time).filter { it.isNotBlank() }.joinToString("  ")
        if (whenLabel.isNotBlank()) {
            Text(
                whenLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            exam.courseName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val where = listOf(exam.place, exam.seatNo.takeIf { it.isNotBlank() }?.let { "座位 $it" }, exam.examType)
            .mapNotNull { it }
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (where.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                where,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreDetailSheet(score: JwxtScore, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(score.courseName, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            AcademicDetailRow("课程号", score.courseNo.ifBlank { "—" })
            AcademicDetailRow("课程类型", score.courseType.ifBlank { "—" })
            AcademicDetailRow("学分", score.credit?.toString() ?: "—")
            AcademicDetailRow("学时", score.creditHours.ifBlank { "—" })
            AcademicDetailRow("成绩", score.score.ifBlank { "—" })
            AcademicDetailRow("状态", score.passStatus.ifBlank { "—" })
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDetailSheet(exam: JwxtExam, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(exam.courseName, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            AcademicDetailRow("课程号", exam.courseNo.ifBlank { "—" })
            AcademicDetailRow("日期", exam.date.ifBlank { "—" })
            AcademicDetailRow("时间", exam.timeDescription.ifBlank { listOf(exam.startTime, exam.endTime).filter { it.isNotBlank() }.joinToString("-").ifBlank { "—" } })
            AcademicDetailRow("地点", exam.place.ifBlank { "—" })
            AcademicDetailRow("座位", exam.seatNo.ifBlank { "—" })
            AcademicDetailRow("周次", exam.week?.let { "第 $it 周" } ?: "—")
            AcademicDetailRow("类型", exam.examType.ifBlank { "—" })
            AcademicDetailRow("教师", exam.teachers.joinToString("、").ifBlank { "—" })
            AcademicDetailRow("状态", exam.status.ifBlank { "—" })
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AcademicDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AcademicTermPicker(
    terms: List<JwxtNamedCode>,
    selectedCode: String?,
    fallbackName: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = terms.firstOrNull { it.code == selectedCode }
    val label = selected?.displayName ?: fallbackName
    Box {
        Row(
            modifier = Modifier
                .widthIn(max = 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = terms.isNotEmpty()) { open = true }
                .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (terms.isNotEmpty()) {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = "切换学期",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            terms.forEach { term ->
                Text(
                    text = term.displayName,
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
