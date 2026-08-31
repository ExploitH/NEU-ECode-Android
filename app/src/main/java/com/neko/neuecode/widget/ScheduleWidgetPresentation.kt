package com.neko.neuecode.widget

import com.neko.neuecode.domain.jwxt.CourseColorHasher
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.SchedulePresentation
import com.neko.neuecode.domain.jwxt.ScheduleTodayItem
import com.neko.neuecode.R
import java.util.Calendar
import java.util.TimeZone

object ScheduleWidgetPresentation {
    private val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    const val noClassCopy = "今日无课"
    const val finishedCopy = "今日课程已上完"
    const val dayEmptyCopy = "当天无课"

    data class DayCard(
        val courseName: String,
        val classroom: String,
        val timeLabel: String,
        val sectionLabel: String,
        val backgroundColor: Int,
        val backgroundResIndex: Int,
        val courseKey: String,
    )

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

    fun todayCards(
        document: JwxtScheduleDocument?,
        actualWeek: Int?,
        todayWeekday: Int,
        nowMinutes: Int = currentMinutesOfDay(),
    ): List<DayCard> {
        if (document == null || actualWeek == null) return emptyList()
        return remainingTodayItems(
            items = SchedulePresentation.todayItems(document, todayWeekday, actualWeek),
            nowMinutes = nowMinutes,
        ).map(::toDayCard)
    }

    fun dayCards(
        document: JwxtScheduleDocument?,
        week: Int?,
        weekday: Int,
    ): List<DayCard> {
        if (document == null || week == null) return emptyList()
        return SchedulePresentation.todayItems(document, weekday, week).map(::toDayCard)
    }

    private fun toDayCard(item: ScheduleTodayItem): DayCard {
        return DayCard(
            courseName = item.courseName,
            classroom = item.classroom,
            timeLabel = "${item.startTime}-${item.endTime}",
            sectionLabel = "第${item.startSection}-${item.endSection}节",
            backgroundColor = pastelColor(item.eventId),
            backgroundResIndex = cardBackgroundIndex(item.eventId),
            courseKey = item.eventId,
        )
    }

    fun pastelColor(courseKey: String): Int {
        val hue = CourseColorHasher.hue(courseKey)
        return hsvToColor(hue, 0.42f, 0.92f)
    }

    fun hsvToColor(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val c = value * saturation
        val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
        val m = value - c
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(part: Float): Int = ((part + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }

    fun cardBackgroundIndex(courseKey: String): Int {
        return ((CourseColorHasher.hue(courseKey) / 60f).toInt() % 6 + 6) % 6
    }

    val cardBackgrounds: IntArray = intArrayOf(
        R.drawable.schedule_day_class_card_bg_0,
        R.drawable.schedule_day_class_card_bg_1,
        R.drawable.schedule_day_class_card_bg_2,
        R.drawable.schedule_day_class_card_bg_3,
        R.drawable.schedule_day_class_card_bg_4,
        R.drawable.schedule_day_class_card_bg_5,
    )

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

    fun todaySubtitle(actualWeek: Int?, todayWeekday: Int, epochDay: Long): String {
        val day = weekdayNames.getOrNull(todayWeekday - 1) ?: "?"
        val date = ScheduleDayPagerPolicy.dateLabel(epochDay)
        return if (actualWeek == null) "开学日前 · $date 周$day" else "第${actualWeek}周 · $date 周$day"
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
