package com.neko.neuecode.widget

import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleTodayItem
import java.util.Calendar
import java.util.TimeZone

object ScheduleWidgetPresentation {
    private val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    const val noClassCopy = "今日无课"
    const val finishedCopy = "今日课程已上完"

    fun todayLines(
        document: JwxtScheduleDocument?,
        actualWeek: Int?,
        todayWeekday: Int,
        nowMinutes: Int = currentMinutesOfDay(),
        limit: Int = 4,
    ): List<String> {
        if (document == null) return listOf("暂无课表缓存", "打开课表同步")
        if (actualWeek == null) return listOf("学期尚未开始", "请在课表设定开学日")
        val items = SchedulePresentation.todayItems(document, todayWeekday, actualWeek)
        if (items.isEmpty()) return listOf(noClassCopy)
        val remaining = remainingTodayItems(items, nowMinutes)
        if (remaining.isEmpty()) return listOf(finishedCopy)
        return remaining.take(limit).map { item ->
            "${item.startTime}-${item.endTime}  ${item.courseName}  ${item.classroom}".trim()
        }
    }

    fun remainingTodayItems(
        items: List<ScheduleTodayItem>,
        nowMinutes: Int,
    ): List<ScheduleTodayItem> {
        return items.filter { item ->
            val end = parseMinutes(item.endTime) ?: return@filter true
            nowMinutes < end
        }
    }

    fun nextRefreshMinutes(
        items: List<ScheduleTodayItem>,
        nowMinutes: Int,
    ): Int? {
        return items.asSequence()
            .flatMap { item ->
                sequenceOf(parseMinutes(item.startTime), parseMinutes(item.endTime))
            }
            .filterNotNull()
            .filter { it > nowMinutes }
            .minOrNull()
    }

    fun todaySubtitle(actualWeek: Int?, todayWeekday: Int): String {
        val day = weekdayNames.getOrNull(todayWeekday - 1) ?: "?"
        return if (actualWeek == null) "开学日前 · 周$day" else "第${actualWeek}周 · 周$day"
    }

    fun weekDayCounts(document: JwxtScheduleDocument?, week: Int): List<Int> {
        val cells = document?.let { SchedulePresentation.cellsForWeek(it, week) }.orEmpty()
        return (1..7).map { day -> cells.count { it.weekday == day } }
    }

    fun weekLines(
        document: JwxtScheduleDocument?,
        week: Int,
        limit: Int = 6,
    ): List<String> {
        if (document == null) return listOf("暂无课表缓存", "打开课表同步")
        val cells = SchedulePresentation.cellsForWeek(document, week)
        if (cells.isEmpty()) return listOf("本周暂无课程")
        return cells.take(limit).map { cell ->
            val day = weekdayNames.getOrNull(cell.weekday - 1) ?: cell.weekday.toString()
            "周$day ${cell.startSection}-${cell.endSection}节 ${cell.courseName}"
        }
    }

    fun parseMinutes(hhmm: String?): Int? {
        if (hhmm.isNullOrBlank()) return null
        val parts = hhmm.trim().split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun currentMinutesOfDay(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        return Calendar.getInstance(timeZone).run {
            timeInMillis = nowMillis
            get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
        }
    }
}
