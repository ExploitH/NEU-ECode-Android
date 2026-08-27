package com.neko.neuecode.domain.jwxt

object ScheduleTodayHighlight {
    fun weekdayToMark(
        selectedWeek: Int,
        actualWeek: Int?,
        todayWeekday: Int,
    ): Int {
        if (actualWeek == null) return 0
        if (selectedWeek != actualWeek) return 0
        return todayWeekday
    }

    fun todayPaneWeek(actualWeek: Int?, @Suppress("UNUSED_PARAMETER") selectedWeek: Int): Int? {
        return actualWeek
    }
}
