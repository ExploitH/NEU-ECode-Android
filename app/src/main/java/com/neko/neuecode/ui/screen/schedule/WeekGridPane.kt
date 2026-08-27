package com.neko.neuecode.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.neuecode.domain.jwxt.CourseColorHasher
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.ScheduleGridCell
import com.neko.neuecode.domain.jwxt.SchedulePresentation

private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private const val WEEKDAY_COUNT = 7
private const val SECTION_COUNT = 12
private val headerRowHeight = 28.dp
private val timeColumnWidth = 28.dp

@Composable
fun WeekGridPane(
    document: JwxtScheduleDocument,
    week: Int,
    todayWeekday: Int,
    onCellClick: (ScheduleGridCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = SchedulePresentation.cellsForWeek(document, week)
    val sectionTimes = document.sections.associate { it.number to it.name }
    val gridLine = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val dayColumnWidth = (maxWidth - timeColumnWidth) / WEEKDAY_COUNT
        val sectionHeight = ((maxHeight - headerRowHeight) / SECTION_COUNT).coerceAtLeast(18.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerRowHeight),
            ) {
                Box(modifier = Modifier.width(timeColumnWidth).fillMaxHeight())
                weekdayLabels.forEachIndexed { index, label ->
                    val weekday = index + 1
                    val highlight = weekday == todayWeekday
                    Box(
                        modifier = Modifier
                            .width(dayColumnWidth)
                            .fillMaxHeight()
                            .background(
                                if (highlight) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            ),
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

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(timeColumnWidth)) {
                    for (section in 1..SECTION_COUNT) {
                        Box(
                            modifier = Modifier
                                .height(sectionHeight)
                                .fillMaxWidth()
                                .border(0.5.dp, gridLine),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = sectionTimes[section] ?: section.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
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
                            .height(sectionHeight * SECTION_COUNT),
                    ) {
                        for (section in 1..SECTION_COUNT) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(sectionHeight)
                                    .align(Alignment.TopStart)
                                    .padding(top = sectionHeight * (section - 1))
                                    .border(0.5.dp, gridLine)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
                            )
                        }
                        dayCells.forEach { cell ->
                            val start = cell.startSection.coerceIn(1, SECTION_COUNT)
                            val end = cell.endSection.coerceIn(start, SECTION_COUNT)
                            val span = (end - start + 1).coerceAtLeast(1)
                            val hue = CourseColorHasher.hue(cell.courseKey)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(sectionHeight * span)
                                    .padding(top = sectionHeight * (start - 1), start = 1.dp, end = 1.dp)
                                    .clip(RectangleShape)
                                    .background(Color.hsl(hue, 0.42f, 0.78f))
                                    .clickable { onCellClick(cell) }
                                    .padding(2.dp),
                            ) {
                                Column {
                                    Text(
                                        text = cell.courseName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start,
                                    )
                                    if (cell.classroom.isNotBlank()) {
                                        Text(
                                            text = cell.classroom,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
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
}
