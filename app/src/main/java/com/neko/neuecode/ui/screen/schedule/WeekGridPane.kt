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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.neuecode.data.local.schedule.WeekStartDay
import com.neko.neuecode.domain.jwxt.CourseColorHasher
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.JwxtSection
import com.neko.neuecode.domain.jwxt.ScheduleGridCell
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleWeekLayout

private const val WEEKDAY_COUNT = 7
private const val SECTION_COUNT = 12
private val headerRowHeight = 56.dp
private val timeColumnWidth = 44.dp
private val slotHeight = 56.dp
private val gap = 4.dp

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
    val sectionTimes = sections.associate { it.number to it.name }
    val maxSection = sections.maxOfOrNull { it.number }?.coerceAtLeast(SECTION_COUNT) ?: SECTION_COUNT
    val colors = MaterialTheme.colorScheme
    val rowH = slotHeight + gap
    val counts = cells.groupingBy { it.weekday }.eachCount()
    val headers = ScheduleWeekLayout.headers(
        weekStartDay = weekStartDay,
        termStartEpochDay = termStartEpochDay,
        week = week,
        courseCounts = counts,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .padding(8.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val dayColumnWidth = ((maxWidth - timeColumnWidth - gap * WEEKDAY_COUNT) / WEEKDAY_COUNT)
                .coerceAtLeast(36.dp)
            val gridH = rowH * maxSection

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
                        val count = header.courseCount
                        Column(
                            modifier = Modifier
                                .width(dayColumnWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (highlight) colors.primaryContainer else colors.surface)
                                .padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "周${header.label}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (highlight) colors.onPrimaryContainer else colors.onSurface,
                                maxLines = 1,
                            )
                            header.dateLabel?.let { date ->
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (highlight) colors.onPrimaryContainer.copy(alpha = 0.85f) else colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = if (count == 0) "无课" else "${count}门",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (highlight) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(gap))

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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(slotHeight)
                                .offset(y = rowH * (section - 1)),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(timeColumnWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceContainerLow)
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = sectionTimes[section] ?: "${section}节",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                            repeat(WEEKDAY_COUNT) {
                                Spacer(modifier = Modifier.width(dayColumnWidth).fillMaxHeight())
                            }
                        }
                    }

                    cells.forEach { cell ->
                        if (cell.weekday !in 1..WEEKDAY_COUNT) return@forEach
                        val start = cell.startSection.coerceIn(1, maxSection)
                        val end = cell.endSection.coerceIn(start, maxSection)
                        val span = (end - start + 1).coerceAtLeast(1)
                        val cardX = timeColumnWidth + gap + (dayColumnWidth + gap) *
                            ScheduleWeekLayout.columnIndex(cell.weekday, weekStartDay)
                        val cardY = rowH * (start - 1)
                        val cardH = rowH * span - gap
                        val hue = CourseColorHasher.hue(cell.courseKey)
                        Box(
                            modifier = Modifier
                                .offset(x = cardX, y = cardY)
                                .width(dayColumnWidth)
                                .height(cardH)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.hsl(hue, 0.42f, 0.78f))
                                .clickable { onCellClick(cell) }
                                .padding(4.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = cell.courseName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (cell.classroom.isNotBlank()) {
                                    Text(
                                        text = cell.classroom,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start,
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
