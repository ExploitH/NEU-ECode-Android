package com.neko.neuecode.ui.screen.schedule

object ScheduleSettingsLayout {
    const val VISIBLE_TERM_ROWS = 5
    const val TERM_ROW_HEIGHT_DP = 32

    fun termListHeightDp(termCount: Int): Int {
        if (termCount <= 0) return 0
        return termCount.coerceAtMost(VISIBLE_TERM_ROWS) * TERM_ROW_HEIGHT_DP
    }

    fun isTermListScrollable(termCount: Int): Boolean {
        return termCount > VISIBLE_TERM_ROWS
    }
}
