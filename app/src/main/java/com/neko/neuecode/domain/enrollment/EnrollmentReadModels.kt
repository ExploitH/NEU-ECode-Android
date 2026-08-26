package com.neko.neuecode.domain.enrollment

data class EnrollmentSection(
    val code: String,
    val name: String,
    val beginTime: String,
    val endTime: String
)

data class EnrollmentSchedule(
    val termName: String,
    val sections: Map<String, EnrollmentSection>,
    val entries: List<EnrollmentScheduleEntry>
)

data class EnrollmentScheduleEntry(
    val teachingClassId: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: String
)

data class EnrollmentSelectedCourse(
    val teachingClassId: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val clazzType: String,
    val sourceLabel: String,
    val credits: Double,
    val selectedCount: Int,
    val capacity: Int,
    val currentWeight: Int?
)

data class EnrollmentCatalogPage(
    val courses: List<EnrollmentCourse>,
    val pageNumber: Int,
    val pageSize: Int,
    val total: Int,
    val hasMore: Boolean
)
