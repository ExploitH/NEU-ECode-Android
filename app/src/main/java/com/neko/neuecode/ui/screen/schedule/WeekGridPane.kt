package com.neko.neuecode.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neko.neuecode.domain.jwxt.CourseColorHasher
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.ScheduleGridCell
import com.neko.neuecode.domain.jwxt.SchedulePresentation

private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val sectionHeight = 56.dp
private val timeColumnWidth = 44.dp
private val dayColumnWidth = 88.dp

@Composable
fun WeekGridPane(
    document: JwxtScheduleDocument,
    week: Int,
    todayWeekday: Int,
    onCellClick: (ScheduleGridCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = SchedulePresentation.cellsForWeek(document, week)
    val maxSection = document.sections.maxOfOrNull { it.number }?.coerceAtLeast(12) ?: 12
    val sectionTimes = document.sections.associate { it.number to it.name }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Box(modifier = Modifier.width(timeColumnWidth))
            weekdayLabels.forEachIndexed { index, label ->
                val weekday = index + 1
                val highlight = weekday == todayWeekday
                Box(
                    modifier = Modifier
                        .width(dayColumnWidth)
                        .padding(2.dp)
                        .background(
                            if (highlight) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.width(timeColumnWidth)) {
                for (section in 1..maxSection) {
                    Box(
                        modifier = Modifier
                            .height(sectionHeight)
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = sectionTimes[section] ?: section.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            weekdayLabels.indices.forEach { index ->
                val weekday = index + 1
                val dayCells = cells.filter { it.weekday == weekday }
                Box(
                    modifier = Modifier
                        .width(dayColumnWidth)
                        .height(sectionHeight * maxSection),
                ) {
                    for (section in 1..maxSection) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sectionHeight)
                                .align(Alignment.TopStart)
                                .padding(top = sectionHeight * (section - 1))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                        )
                    }
                    dayCells.forEach { cell ->
                        val span = (cell.endSection - cell.startSection + 1).coerceAtLeast(1)
                        val hue = CourseColorHasher.hue(cell.courseKey)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sectionHeight * span)
                                .padding(top = sectionHeight * (cell.startSection - 1), start = 2.dp, end = 2.dp)
                                .clip(RectangleShape)
                                .background(Color.hsl(hue, 0.42f, 0.78f))
                                .clickable { onCellClick(cell) }
                                .padding(4.dp),
                        ) {
                            Column {
                                Text(
                                    text = cell.courseName,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (cell.classroom.isNotBlank()) {
                                    Text(
                                        text = cell.classroom,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
