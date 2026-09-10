package com.neko.neuecode.domain.jwxt

enum class AcademicKind {
    Scores,
    Exams,
    ;

    val route: String
        get() = when (this) {
            Scores -> "scores"
            Exams -> "exams"
        }

    val title: String
        get() = when (this) {
            Scores -> "成绩"
            Exams -> "考试"
        }

    companion object {
        fun fromRoute(value: String?): AcademicKind {
            return if (value.equals("exams", ignoreCase = true)) Exams else Scores
        }
    }
}
