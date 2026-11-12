package com.neko.neuecode.domain.jwxt

import java.util.Calendar
import java.util.TimeZone

object ExamWidgetVisibility {
    const val RETENTION_DAYS = 7L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun visible(exam: JwxtExam, nowMillis: Long, timeZone: TimeZone): Boolean {
        val end = endMillis(exam, timeZone) ?: return true
        return nowMillis <= end + RETENTION_DAYS * DAY_MS
    }

    fun nextChangeMillis(exams: List<JwxtExam>, nowMillis: Long, timeZone: TimeZone): Long? {
        return exams.mapNotNull { exam ->
            endMillis(exam, timeZone)?.plus(RETENTION_DAYS * DAY_MS)?.takeIf { it > nowMillis }
        }.minOrNull()
    }

    private fun endMillis(exam: JwxtExam, timeZone: TimeZone): Long? {
        val date = exam.date.trim().take(10)
        val parts = date.split('-')
        if (parts.size != 3 || parts.any { it.toIntOrNull() == null }) return null
        val clock = exam.endTime.ifBlank { clockFromDescription(exam.timeDescription) }
        val hourMinute = clock.split(':')
        val calendar = Calendar.getInstance(timeZone)
        calendar.clear()
        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        if (hourMinute.size >= 2 && hourMinute[0].toIntOrNull() != null && hourMinute[1].toIntOrNull() != null) {
            calendar.set(Calendar.HOUR_OF_DAY, hourMinute[0].toInt())
            calendar.set(Calendar.MINUTE, hourMinute[1].toInt())
        } else {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.timeInMillis
    }

    private fun clockFromDescription(description: String): String {
        return Regex("""\d{1,2}:\d{2}\s*-\s*(\d{1,2}:\d{2})""").find(description)?.groupValues?.get(1).orEmpty()
    }
}
