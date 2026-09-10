package com.neko.neuecode.domain.jwxt

data class AcademicSyncProgress(
    val step: Int,
    val total: Int,
    val label: String,
) {
    val line: String
        get() = "$step/$total $label"

    companion object {
        const val TOTAL = 5

        fun probing() = AcademicSyncProgress(1, TOTAL, "正在检测校园网…")
        fun loggingIn() = AcademicSyncProgress(2, TOTAL, "正在登录教务…")
        fun currentTerm() = AcademicSyncProgress(3, TOTAL, "正在查询学期…")
        fun downloading(kind: AcademicKind) = AcademicSyncProgress(
            4,
            TOTAL,
            if (kind == AcademicKind.Scores) "正在下载成绩…" else "正在下载考试安排…",
        )
        fun arranging(kind: AcademicKind) = AcademicSyncProgress(
            5,
            TOTAL,
            if (kind == AcademicKind.Scores) "正在整理成绩…" else "正在整理考试…",
        )
    }
}
