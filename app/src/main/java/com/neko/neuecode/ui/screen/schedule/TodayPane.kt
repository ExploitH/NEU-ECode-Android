package com.neko.neuecode.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neko.neuecode.data.local.schedule.WeekStartDay
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleTodayItem
import com.neko.neuecode.ui.components.EmptyState
import com.neko.neuecode.ui.theme.CoursePalette
import com.neko.neuecode.ui.theme.panel

@Composable
fun TodayPane(
    document: JwxtScheduleDocument,
    weekday: Int,
    week: Int,
    onItemClick: (ScheduleTodayItem) -> Unit,
    modifier: Modifier = Modifier,
    weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    termStartEpochDay: Long? = null,
) {
    val items = SchedulePresentation.todayItems(
        document,
        weekday = weekday,
        week = week,
        weekStartDay = weekStartDay,
        termStartEpochDay = termStartEpochDay,
    )
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.WbSunny,
            title = "今天没有课",
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
    ) {
        items(items, key = { it.eventId }) { item ->
            TodayCourseRow(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun TodayCourseRow(item: ScheduleTodayItem, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tone = CoursePalette.tone(item.courseKey.ifBlank { item.eventId })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.width(52.dp)) {
            Text(
                text = item.startTime,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Text(
                text = item.endTime,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(tone.content.copy(alpha = 0.55f)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.courseName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val meta = listOf(
                item.classroom,
                "第${item.startSection}-${item.endSection}节",
                item.teachers.joinToString("、"),
            ).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
