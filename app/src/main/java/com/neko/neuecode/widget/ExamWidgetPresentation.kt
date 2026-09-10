package com.neko.neuecode.widget

import com.neko.neuecode.domain.jwxt.JwxtExamDocument
import com.neko.neuecode.domain.jwxt.displayName

object ExamWidgetPresentation {
    const val kicker = "我的考试"
    const val emptyTermCopy = "本学期暂无考试安排"
    const val missingCacheTitle = "暂无考试缓存"
    const val missingCacheCopy = "打开考试同步"

    data class Card(
        val courseName: String,
        val meta: String,
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
                val time = exam.timeDescription.ifBlank {
                    listOf(exam.startTime, exam.endTime).filter { it.isNotBlank() }.joinToString("-")
                }
                val seat = exam.seatNo.takeIf { it.isNotBlank() }?.let { "座位 $it" }
                Card(
                    courseName = exam.courseName,
                    meta = listOfNotNull(
                        exam.date.takeIf { it.isNotBlank() },
                        time.takeIf { it.isNotBlank() },
                        exam.place.takeIf { it.isNotBlank() },
                        seat,
                    ).joinToString("  "),
                    backgroundResIndex = ScheduleWidgetPresentation.cardBackgroundIndex(exam.courseNo.ifBlank { exam.courseName }),
                )
            }
    }
}
