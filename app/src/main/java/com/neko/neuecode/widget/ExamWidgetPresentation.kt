package com.neko.neuecode.widget

import com.neko.neuecode.domain.jwxt.ExamDisplayFormatter
import com.neko.neuecode.domain.jwxt.JwxtExamDocument
import com.neko.neuecode.domain.jwxt.displayName

object ExamWidgetPresentation {
    const val kicker = "我的考试"
    const val emptyTermCopy = "本学期暂无考试安排"
    const val missingCacheTitle = "暂无考试缓存"
    const val missingCacheCopy = "打开考试同步"

    data class Card(
        val courseName: String,
        val dateLine: String,
        val timeLine: String,
        val placeLine: String,
        val backgroundResIndex: Int,
    )

    fun title(document: JwxtExamDocument?): String {
        return document?.term?.displayName?.takeIf { it.isNotBlank() } ?: missingCacheTitle
    }

    fun emptyCopy(document: JwxtExamDocument?): String {
        if (document == null) return missingCacheCopy
        if (document.exams.isEmpty()) return emptyTermCopy
        return ""
    }

    fun cards(document: JwxtExamDocument?, limit: Int = 6): List<Card> {
        if (document == null) return emptyList()
        return document.exams
            .sortedWith(compareBy({ it.date }, { it.startTime }, { it.courseNo }))
            .take(limit)
            .map { exam ->
                Card(
                    courseName = exam.courseName,
                    dateLine = ExamDisplayFormatter.displayDate(exam.date),
                    timeLine = ExamDisplayFormatter.displayTime(exam),
                    placeLine = ExamDisplayFormatter.displayPlace(exam),
                    backgroundResIndex = ScheduleWidgetPresentation.cardBackgroundIndex(
                        exam.courseNo.ifBlank { exam.courseName },
                    ),
                )
            }
    }
}
