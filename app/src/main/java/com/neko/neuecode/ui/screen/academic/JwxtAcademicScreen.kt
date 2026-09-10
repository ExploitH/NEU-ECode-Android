package com.neko.neuecode.ui.screen.academic

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
                    Text(
                        text = state.message.ifBlank { "尚未同步${state.kind.title}" },
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                state.kind == AcademicKind.Scores -> {
                    val document = state.scores!!
                    if (document.scores.isEmpty()) {
                        Text(
                            text = "本学期暂无可查询成绩",
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                Text(
                                    text = "${document.term.displayName} · ${document.summary.count} 门",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
                        Text(
                            text = "本学期暂无可查询考试",
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                Text(
                                    text = "${document.term.displayName} · ${document.summary.count} 场",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(score.courseName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    score.courseNo.takeIf { it.isNotBlank() },
                    score.courseType.takeIf { it.isNotBlank() },
                    score.credit?.let { "$it 学分" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOf(score.score, score.passStatus).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExamCard(exam: JwxtExam, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(exam.courseName, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(exam.date, exam.timeDescription.ifBlank { listOf(exam.startTime, exam.endTime).filter { it.isNotBlank() }.joinToString("-") })
                    .filter { it.isNotBlank() }
                    .joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                listOf(exam.place, exam.seatNo.takeIf { it.isNotBlank() }?.let { "座位 $it" }, exam.examType)
                    .mapNotNull { it }
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
