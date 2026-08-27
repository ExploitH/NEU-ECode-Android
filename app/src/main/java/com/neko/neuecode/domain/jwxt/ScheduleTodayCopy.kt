package com.neko.neuecode.domain.jwxt

object ScheduleTodayCopy {
    const val MISSING_TERM_START =
        "请先在「课表设定」填写学期开始日期，才能确定今天是第几周。"
    const val TERM_NOT_STARTED =
        "学期尚未开始，开学后再来看今日课程。"

    fun todayUnavailableMessage(
        termStartEpochDay: Long?,
        actualWeek: Int?,
    ): String? {
        if (actualWeek != null) return null
        return if (termStartEpochDay != null) TERM_NOT_STARTED else MISSING_TERM_START
    }
}
