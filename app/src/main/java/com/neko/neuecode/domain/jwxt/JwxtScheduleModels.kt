package com.neko.neuecode.domain.jwxt

data class JwxtNamedCode(
    val code: String,
    val name: String
)

data class JwxtScheduleSummary(
    val courseCount: Int,
    val eventCount: Int,
    val notArrangedCount: Int,
    val practiceCount: Int
)

data class JwxtSection(
    val number: Int,
    val name: String,
    val enabled: Boolean,
    val sourceId: String
)

data class JwxtCourse(
    val courseKey: String,
    val courseCode: String,
    val courseName: String,
    val teachingClassId: String,
    val courseSerialNo: String,
    val teachingClassName: String,
    val teachingTarget: String,
    val credit: Double?,
    val teachers: List<String>,
    val eventIds: List<String>
)

data class JwxtSections(
    val start: Int,
    val end: Int
)

data class JwxtTimeRange(
    val start: String,
    val end: String
)

data class JwxtScheduleEvent(
    val id: String,
    val courseCode: String,
    val courseName: String,
    val teachingClassId: String,
    val courseSerialNo: String,
    val teachingClassName: String,
    val teachingTarget: String,
    val credit: Double?,
    val weekday: Int,
    val weekdayName: String,
    val sections: JwxtSections,
    val time: JwxtTimeRange,
    val campus: String,
    val classroom: String,
    val weekSpec: String,
    val weeks: List<Int>,
    val teachers: List<String>,
    val assessment: String,
    val grading: String,
    val details: List<String>,
    val weekTeacherClassroomDetails: List<String>
)

data class JwxtScheduleDocument(
    val schemaVersion: String,
    val source: String,
    val generatedAt: String,
    val account: String,
    val term: JwxtNamedCode,
    val campus: JwxtNamedCode,
    val summary: JwxtScheduleSummary,
    val sections: List<JwxtSection>,
    val courses: List<JwxtCourse>,
    val events: List<JwxtScheduleEvent>,
    val notArrangedCount: Int,
    val practiceCount: Int
) {
    fun toDebugSnapshot(): String {
        return buildString {
            append("schema=").append(schemaVersion)
            append(";term=").append(term.code)
            append(";campus=").append(campus.code)
            append(";courses=").append(courses.size)
            append(";events=").append(events.size)
            courses.forEach { course ->
                append(";course=").append(course.courseCode)
                append("/").append(course.courseName)
            }
            events.forEach { event ->
                append(";event=").append(event.id)
                append("/").append(event.courseName)
                append("/").append(event.weekdayName)
                append("/").append(event.classroom)
            }
        }
    }
}
