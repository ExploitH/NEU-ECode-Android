package com.neko.neuecode.domain.jwxt

/**
 * Home-widget / exam-page display helpers. JWXT sometimes stores the exam
 * calendar day as `yyyy-MM-dd 00:00:00` and packs the session label into
 * `examTimeDescription`; the widget used to dump that into one truncated line.
 */
object ExamDisplayFormatter {
    private val datePrefix = Regex("""^(\d{4}-\d{2}-\d{2})""")
    private val clockRange = Regex("""\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}""")

    fun displayDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return datePrefix.find(trimmed)?.groupValues?.get(1) ?: trimmed.substringBefore(' ').trim()
    }

    fun displayTime(exam: JwxtExam): String {
        val clock = listOf(exam.startTime, exam.endTime)
            .filter { it.isNotBlank() }
            .joinToString("-")
        val description = exam.timeDescription.trim()
        val clockFromDescription = clockRange.find(description)?.value
            ?.replace(Regex("""\s*-\s*"""), "-")
            ?.replace(" ", "")
        val session = description
            .replace(clockRange, " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '·', '-', '—', ',', '，')
        val time = clock.ifBlank { clockFromDescription.orEmpty() }
        return listOf(time, session).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun displayPlace(exam: JwxtExam): String {
        val seat = exam.seatNo.takeIf { it.isNotBlank() }?.let { "座位 $it" }
        return listOfNotNull(exam.place.takeIf { it.isNotBlank() }, seat).joinToString(" · ")
    }
}
