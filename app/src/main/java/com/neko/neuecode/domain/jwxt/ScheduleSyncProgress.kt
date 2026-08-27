package com.neko.neuecode.domain.jwxt

data class ScheduleSyncProgress(
    val step: Int,
    val total: Int,
    val label: String,
) {
    val line: String
        get() = "$step/$total $label"

    companion object {
        const val TOTAL = 7

        fun probing() = ScheduleSyncProgress(1, TOTAL, "正在检测校园网…")
        fun loggingIn() = ScheduleSyncProgress(2, TOTAL, "正在登录教务…")
        fun currentTerm() = ScheduleSyncProgress(3, TOTAL, "正在查询当前学期…")
        fun campuses() = ScheduleSyncProgress(4, TOTAL, "正在查询上课校区…")
        fun sections() = ScheduleSyncProgress(5, TOTAL, "正在获取上课节次…")
        fun details() = ScheduleSyncProgress(6, TOTAL, "正在下载课程明细…")
        fun arranging() = ScheduleSyncProgress(7, TOTAL, "正在整理课表…")
    }
}
