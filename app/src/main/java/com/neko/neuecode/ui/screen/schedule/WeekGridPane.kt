/*
 * Week grid adapted from Sleepy (https://github.com/lingion/sleepy)
 * Copyright (C) Lingion and contributors
 * Licensed under the GNU General Public License v3.0.
 *
 * Changes for NEU eCode: JWXT domain models, 7 weekday columns always
 * visible, 12 scrollable period rows, no Sleepy table/prefs layer.
 */
package com.neko.neuecode.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.neuecode.data.local.schedule.WeekStartDay
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.JwxtSection
import com.neko.neuecode.domain.jwxt.ScheduleGridCell
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleWeekLayout
import com.neko.neuecode.ui.theme.CoursePalette
import com.neko.neuecode.ui.theme.panel

private const val WEEKDAY_COUNT = 7
private const val SECTION_COUNT = 12
private val headerRowHeight = 44.dp
private val timeColumnWidth = 28.dp
private val slotHeight = 56.dp
private val gap = 2.dp

@Composable
fun WeekGridPane(
    document: JwxtScheduleDocument,
    week: Int,
    todayWeekday: Int,
    onCellClick: (ScheduleGridCell) -> Unit,
    modifier: Modifier = Modifier,
    weekStartDay: WeekStartDay = WeekStartDay.SUNDAY,
    termStartEpochDay: Long? = null,
) {
    WeekGridPane(
        cells = SchedulePresentation.cellsForWeek(
            document = document,
            week = week,
            weekStartDay = weekStartDay,
            termStartEpochDay = termStartEpochDay,
        ),
        sections = document.sections,
        todayWeekday = todayWeekday,
        weekStartDay = weekStartDay,
        termStartEpochDay = termStartEpochDay,
        week = week,
        onCellClick = onCellClick,
        modifier = modifier,
    )
}

@Composable
fun WeekGridPane(
    cells: List<ScheduleGridCell>,
    sections: List<JwxtSection>,
    todayWeekday: Int,
    onCellClick: (ScheduleGridCell) -> Unit,
    modifier: Modifier = Modifier,
    weekStartDay: WeekStartDay = WeekStartDay.SUNDAY,
    termStartEpochDay: Long? = null,
    week: Int = 1,
) {
    val maxSection = sections.maxOfOrNull { it.number }?.coerceAtLeast(SECTION_COUNT) ?: SECTION_COUNT
    val colors = MaterialTheme.colorScheme
    val rowH = slotHeight + gap
    val headers = ScheduleWeekLayout.headers(
        weekStartDay = weekStartDay,
        termStartEpochDay = termStartEpochDay,
        week = week,
        courseCounts = emptyMap(),
    )
    val todayColumn = headers.indexOfFirst { it.weekday == todayWeekday }
    val lineColor = colors.outlineVariant.copy(alpha = 0.45f)
    val todayTint = colors.primary.copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panel)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val dayColumnWidth = ((maxWidth - timeColumnWidth - gap * WEEKDAY_COUNT) / WEEKDAY_COUNT)
                .coerceAtLeast(36.dp)
            val gridH = rowH * maxSection
            val columnX = { index: Int -> timeColumnWidth + gap + (dayColumnWidth + gap) * index }

            // Today's column is tinted from header to last period.
            if (todayColumn >= 0) {
                Box(
                    modifier = Modifier
                        .offset(x = columnX(todayColumn) - gap / 2)
                        .width(dayColumnWidth + gap)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(todayTint),
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(timeColumnWidth))
                    headers.forEach { header ->
                        val highlight = header.weekday == todayWeekday
                        Column(
                            modifier = Modifier.width(dayColumnWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "周${header.label}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (highlight) colors.primary else colors.onSurface,
                                maxLines = 1,
                            )
                            header.dateLabel?.let { date ->
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (highlight) colors.primary else colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridH),
                    ) {
                    for (section in 1..maxSection) {
                        if (section > 1) {
                            Box(
                                modifier = Modifier
                                    .offset(x = timeColumnWidth, y = rowH * (section - 1) - gap / 2)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(lineColor),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .offset(y = rowH * (section - 1))
                                .width(timeColumnWidth)
                                .height(slotHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = section.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }

                    cells.forEach { cell ->
                        if (cell.weekday !in 1..WEEKDAY_COUNT) return@forEach
                        val start = cell.startSection.coerceIn(1, maxSection)
                        val end = cell.endSection.coerceIn(start, maxSection)
                        val span = (end - start + 1).coerceAtLeast(1)
                        val cardX = columnX(ScheduleWeekLayout.columnIndex(cell.weekday, weekStartDay))
                        val cardY = rowH * (start - 1)
                        val cardH = rowH * span - gap
                        val tone = CoursePalette.tone(cell.courseKey)
                        Column(
                            modifier = Modifier
                                .offset(x = cardX, y = cardY)
                                .width(dayColumnWidth)
                                .height(cardH)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tone.container)
                                .clickable { onCellClick(cell) }
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = cell.courseName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                ),
                                color = tone.content,
                                maxLines = if (span >= 2) 4 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (cell.classroom.isNotBlank()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = cell.classroom,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                    ),
                                    color = tone.content.copy(alpha = 0.78f),
                                    maxLines = 2,
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
