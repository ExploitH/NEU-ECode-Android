package com.neko.neuecode.domain.jwxt

import com.neko.neuecode.data.local.schedule.WeekStartDay
import java.util.Calendar
import java.util.TimeZone

data class WeekdayHeader(
    val weekday: Int,
    val label: String,
    val dateLabel: String?,
    val courseCount: Int,
)

object ScheduleWeekLayout {
    private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    fun columnWeekdays(weekStartDay: WeekStartDay): List<Int> {
        return when (weekStartDay) {
            WeekStartDay.SUNDAY -> listOf(7, 1, 2, 3, 4, 5, 6)
            WeekStartDay.MONDAY -> listOf(1, 2, 3, 4, 5, 6, 7)
        }
    }

    fun columnIndex(weekday: Int, weekStartDay: WeekStartDay): Int {
        val index = columnWeekdays(weekStartDay).indexOf(weekday)
        return if (index >= 0) index else 0
    }

    fun shortDateLabel(epochDay: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = epochDay * 86_400_000L
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$month.$day"
    }

    fun displayWeekStartEpochDay(
        termStartEpochDay: Long?,
        week: Int,
        weekStartDay: WeekStartDay,
    ): Long? {
        if (termStartEpochDay == null || week < 1) return null
        val desiredStart = when (weekStartDay) {
            WeekStartDay.SUNDAY -> 7
            WeekStartDay.MONDAY -> 1
        }
        val startWeekday = ScheduleWeekClock.weekdayOf(termStartEpochDay)
        val daysBack = (startWeekday - desiredStart + 7) % 7
        return termStartEpochDay - daysBack + (week - 1L) * 7L
    }

    fun weekdayEpochDay(
        termStartEpochDay: Long?,
        week: Int,
        weekday: Int,
        weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    ): Long? {
        if (weekday !in 1..7) return null
        val weekStart = displayWeekStartEpochDay(termStartEpochDay, week, weekStartDay) ?: return null
        val startWeekday = ScheduleWeekClock.weekdayOf(weekStart)
        val offset = (weekday - startWeekday + 7) % 7
        return weekStart + offset
    }

    fun displayWeekOfOccurrence(
        termStartEpochDay: Long?,
        academicWeek: Int,
        weekday: Int,
        weekStartDay: WeekStartDay,
    ): Int? {
        if (academicWeek < 1 || weekday !in 1..7) return null
        if (termStartEpochDay == null || weekStartDay == WeekStartDay.MONDAY) return academicWeek
        val date = weekdayEpochDay(
            termStartEpochDay = termStartEpochDay,
            week = academicWeek,
            weekday = weekday,
            weekStartDay = WeekStartDay.MONDAY,
        ) ?: return academicWeek
        val firstStart = displayWeekStartEpochDay(termStartEpochDay, 1, weekStartDay) ?: return academicWeek
        if (date < firstStart) return 1
        return ((date - firstStart) / 7L + 1L).toInt()
    }

    fun headers(
        weekStartDay: WeekStartDay,
        termStartEpochDay: Long?,
        week: Int,
        courseCounts: Map<Int, Int>,
    ): List<WeekdayHeader> {
        return columnWeekdays(weekStartDay).map { weekday ->
            WeekdayHeader(
                weekday = weekday,
                label = weekdayLabels.getOrElse(weekday - 1) { "?" },
                dateLabel = weekdayEpochDay(termStartEpochDay, week, weekday, weekStartDay)?.let(::shortDateLabel),
                courseCount = courseCounts[weekday] ?: 0,
            )
        }
    }
}
