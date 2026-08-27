package com.neko.neuecode.widget

import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.SchedulePresentation

object ScheduleWidgetPresentation {
    private val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

    fun todayLines(
        document: JwxtScheduleDocument?,
        actualWeek: Int?,
        todayWeekday: Int,
        limit: Int = 4,
    ): List<String> {
        if (document == null) return listOf("暂无课表缓存", "打开课表同步")
        if (actualWeek == null) return listOf("学期尚未开始", "请在课表设定开学日")
        val items = SchedulePresentation.todayItems(document, todayWeekday, actualWeek)
        if (items.isEmpty()) return listOf("今天没有课")
        return items.take(limit).map { item ->
            "${item.startTime}-${item.endTime}  ${item.courseName}  ${item.classroom}".trim()
        }
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
}
