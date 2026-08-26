package com.neko.neuecode.ui.screen.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleTodayItem

@Composable
fun TodayPane(
    document: JwxtScheduleDocument,
    weekday: Int,
    week: Int,
    onItemClick: (ScheduleTodayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = SchedulePresentation.todayItems(document, weekday = weekday, week = week)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "今天 ${items.size} 节",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                text = "今天没有课",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.eventId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.courseName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${item.startTime}-${item.endTime}  第${item.startSection}-${item.endSection}节",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                listOf(item.classroom, item.teachers.joinToString("、"))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
