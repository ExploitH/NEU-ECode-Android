package com.neko.neuecode.domain.jwxt

data class ScheduleGridCell(
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val courseName: String,
    val classroom: String,
    val courseKey: String,
    val eventId: String,
)

data class ScheduleTodayItem(
    val eventId: String,
    val courseName: String,
    val classroom: String,
    val teachers: List<String>,
    val startTime: String,
    val endTime: String,
    val startSection: Int,
    val endSection: Int,
)

data class CourseDetail(
    val courseName: String,
    val teachers: List<String>,
    val classroom: String,
    val weekSpec: String,
    val sectionsLabel: String,
    val timeLabel: String,
    val credit: Double?,
    val assessment: String,
    val weekdayName: String,
)

object SchedulePresentation {
    fun cellsForWeek(document: JwxtScheduleDocument, week: Int): List<ScheduleGridCell> {
        val target = week.coerceAtLeast(1)
        return document.events
            .filter { target in it.weeks }
            .sortedWith(compareBy({ it.weekday }, { it.sections.start }))
            .map { event ->
                ScheduleGridCell(
                    weekday = event.weekday,
                    startSection = event.sections.start,
                    endSection = event.sections.end,
                    courseName = event.courseName,
                    classroom = event.classroom,
                    courseKey = courseKey(event),
                    eventId = event.id,
                )
            }
    }

    fun cellsByWeek(document: JwxtScheduleDocument, maxWeek: Int): List<List<ScheduleGridCell>> {
        val pages = maxWeek.coerceAtLeast(1)
        val buckets = List(pages) { mutableListOf<ScheduleGridCell>() }
        for (event in document.events) {
            val cell = ScheduleGridCell(
                weekday = event.weekday,
                startSection = event.sections.start,
                endSection = event.sections.end,
                courseName = event.courseName,
                classroom = event.classroom,
                courseKey = courseKey(event),
                eventId = event.id,
            )
            for (week in event.weeks) {
                if (week in 1..pages) {
                    buckets[week - 1].add(cell)
                }
            }
        }
        return buckets.map { weekCells ->
            weekCells.sortedWith(compareBy({ it.weekday }, { it.startSection }))
        }
    }

    fun todayItems(document: JwxtScheduleDocument, weekday: Int, week: Int): List<ScheduleTodayItem> {
        return document.events
            .filter { it.weekday == weekday && week in it.weeks }
            .sortedBy { it.sections.start }
            .map { event ->
                ScheduleTodayItem(
                    eventId = event.id,
                    courseName = event.courseName,
                    classroom = event.classroom,
                    teachers = event.teachers,
                    startTime = event.time.start,
                    endTime = event.time.end,
                    startSection = event.sections.start,
                    endSection = event.sections.end,
                )
            }
    }

    fun detail(event: JwxtScheduleEvent): CourseDetail {
        return CourseDetail(
            courseName = event.courseName,
            teachers = event.teachers,
            classroom = event.classroom,
            weekSpec = event.weekSpec,
            sectionsLabel = "第${event.sections.start}-${event.sections.end}节",
            timeLabel = "${event.time.start}-${event.time.end}",
            credit = event.credit,
            assessment = event.assessment,
            weekdayName = event.weekdayName,
        )
    }

    private fun courseKey(event: JwxtScheduleEvent): String {
        return "${event.courseCode}:${event.teachingClassId}"
    }
}
