package com.neko.neuecode.ui.screen.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neko.neuecode.domain.jwxt.CourseDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    detail: CourseDetail,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(detail.courseName, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow("教师", detail.teachers.joinToString("、").ifBlank { "—" })
            DetailRow("教室", detail.classroom.ifBlank { "—" })
            DetailRow("周次", detail.weekSpec.ifBlank { "—" })
            DetailRow("节次", detail.sectionsLabel)
            DetailRow("时间", "${detail.weekdayName} ${detail.timeLabel}")
            DetailRow("学分", detail.credit?.toString() ?: "—")
            DetailRow("考核", detail.assessment.ifBlank { "—" })
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
