package com.neko.neuecode.ui.screen.schedule

object WeekPagerIndex {
    fun pageOf(week: Int, maxWeek: Int): Int {
        val max = maxWeek.coerceAtLeast(1)
        return (week - 1).coerceIn(0, max - 1)
    }

    fun weekOf(page: Int, maxWeek: Int): Int {
        val max = maxWeek.coerceAtLeast(1)
        return (page + 1).coerceIn(1, max)
    }
}
