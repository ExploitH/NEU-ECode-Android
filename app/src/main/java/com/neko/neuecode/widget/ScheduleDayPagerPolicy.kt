package com.neko.neuecode.widget

object ScheduleDayPagerPolicy {
    const val prevAction = "com.neko.neuecode.widget.ACTION_DAY_PREV"
    const val todayAction = "com.neko.neuecode.widget.ACTION_DAY_TODAY"
    const val nextAction = "com.neko.neuecode.widget.ACTION_DAY_NEXT"

    private val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

    fun normalizeOffset(offset: Int?): Int = offset ?: 0

    fun shift(current: Int, delta: Int): Int = current + delta

    fun todayOffset(): Int = 0

    fun isToday(offset: Int): Boolean = offset == 0

    fun selectedEpochDay(todayEpochDay: Long, offset: Int): Long = todayEpochDay + offset

    fun title(offset: Int, weekday: Int, week: Int?): String {
        val day = weekdayNames.getOrNull(weekday - 1) ?: "?"
        return when (offset) {
            0 -> "今日 · 周$day"
            -1 -> "昨天 · 周$day"
            1 -> "明天 · 周$day"
            else -> if (week == null) "周$day" else "第${week}周 · 周$day"
        }
    }

    fun acceptsPagerAction(action: String?): Boolean {
        return action == prevAction || action == todayAction || action == nextAction
    }

    fun offsetAfter(action: String?, current: Int): Int {
        return when (action) {
            prevAction -> shift(current, -1)
            nextAction -> shift(current, 1)
            todayAction -> todayOffset()
            else -> current
        }
    }
}
