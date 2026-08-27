package com.neko.neuecode.data.remote.jwxt

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.neko.neuecode.domain.jwxt.JwxtCourse
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.jwxt.JwxtScheduleEvent
import com.neko.neuecode.domain.jwxt.JwxtScheduleSummary
import com.neko.neuecode.domain.jwxt.JwxtSection
import com.neko.neuecode.domain.jwxt.JwxtSections
import com.neko.neuecode.domain.jwxt.JwxtTimeRange
import java.security.MessageDigest

object JwxtScheduleNormalizer {

    private val weekdayNames = mapOf(
        1 to "星期一",
        2 to "星期二",
        3 to "星期三",
        4 to "星期四",
        5 to "星期五",
        6 to "星期六",
        7 to "星期日"
    )

    fun expandWeeks(spec: String): List<Int> {
        val source = spec.replace("，", ",")
        val odd = source.contains("单")
        val even = source.contains("双")
        val weeks = linkedSetOf<Int>()
        val range = Regex("""(\d+)(?:\s*[-~至]\s*(\d+))?""")
        for (token in source.split(",")) {
            val match = range.find(token) ?: continue
            var start = match.groupValues[1].toInt()
            var end = match.groupValues[2].ifBlank { match.groupValues[1] }.toInt()
            if (end < start) {
                val swapped = start
                start = end
                end = swapped
            }
            for (week in start..end) {
                if (odd && week % 2 == 0) continue
                if (even && week % 2 == 1) continue
                weeks.add(week)
            }
        }
        return weeks.sorted()
    }

    fun normalize(
        account: String,
        termCode: String,
        termName: String,
        campusCode: String,
        campusName: String,
        sections: List<JsonObject>,
        schedule: JsonObject,
        generatedAt: String
    ): JwxtScheduleDocument {
        val arranged = schedule.arrayOrEmpty("arrangedList")
        val notArranged = schedule.arrayOrEmpty("notArrangeList")
        val practice = schedule.arrayOrEmpty("practiceList")

        val events = arranged.map { rawElement ->
            val raw = rawElement.asJsonObject
            val weekday = raw.intOrZero("dayOfWeek")
            val details = raw.stringList("titleDetail")
            var assessment = ""
            var grading = ""
            val last = details.lastOrNull()
            if (last != null && "/" in last) {
                val parts = last.split("/", limit = 2)
                assessment = parts[0].trim()
                grading = parts.getOrNull(1)?.trim().orEmpty()
            }
            val weekSpec = weekSpec(raw.stringOrEmpty("weeksAndTeachers"))
            val eventWithoutId = JwxtScheduleEvent(
                id = "",
                courseCode = raw.stringOrEmpty("courseCode"),
                courseName = raw.stringOrEmpty("courseName"),
                teachingClassId = raw.stringOrEmpty("teachClassId"),
                courseSerialNo = raw.stringOrEmpty("courseSerialNo"),
                teachingClassName = raw.stringOrEmpty("teachClassName"),
                teachingTarget = raw.stringOrEmpty("teachingTarget"),
                credit = raw.numberOrNull("credit"),
                weekday = weekday,
                weekdayName = weekdayNames[weekday].orEmpty(),
                sections = JwxtSections(
                    start = raw.intOrZero("beginSection"),
                    end = raw.intOrZero("endSection")
                ),
                time = JwxtTimeRange(
                    start = raw.stringOrEmpty("beginTime"),
                    end = raw.stringOrEmpty("endTime")
                ),
                campus = raw.stringOrEmpty("campusName").ifBlank { campusName },
                classroom = raw.stringOrEmpty("placeName"),
                weekSpec = weekSpec,
                weeks = expandWeeks(weekSpec),
                teachers = teachers(raw.stringOrEmpty("weeksAndTeachers")),
                assessment = assessment,
                grading = grading,
                details = details,
                weekTeacherClassroomDetails = raw.stringList("titleWeekTeacherClassroomDetail")
            )
            eventWithoutId.copy(id = eventId(eventWithoutId))
        }.sortedWith(
            compareBy(
                { it.weekday },
                { it.sections.start },
                { it.courseCode },
                { it.weekSpec },
                { it.id }
            )
        )

        val grouped = linkedMapOf<String, JwxtCourse>()
        for (event in events) {
            val key = "${event.courseCode}:${event.teachingClassId}"
            val existing = grouped[key]
            if (existing == null) {
                grouped[key] = JwxtCourse(
                    courseKey = key,
                    courseCode = event.courseCode,
                    courseName = event.courseName,
                    teachingClassId = event.teachingClassId,
                    courseSerialNo = event.courseSerialNo,
                    teachingClassName = event.teachingClassName,
                    teachingTarget = event.teachingTarget,
                    credit = event.credit,
                    teachers = event.teachers,
                    eventIds = listOf(event.id)
                )
            } else {
                grouped[key] = existing.copy(
                    eventIds = existing.eventIds + event.id,
                    teachers = (existing.teachers + event.teachers).distinct()
                )
            }
        }

        val normalizedSections = sections.map { item ->
            JwxtSection(
                number = item.intOrZero("code").takeIf { it != 0 } ?: item.intOrZero("sort"),
                name = item.stringOrEmpty("name"),
                enabled = item.get("enable")?.takeUnless { it.isJsonNull }?.asBoolean ?: true,
                sourceId = item.stringOrEmpty("id")
            )
        }.sortedBy { it.number }

        val courses = grouped.values.sortedWith(compareBy({ it.courseCode }, { it.teachingClassId }))
        return JwxtScheduleDocument(
            schemaVersion = "1.0.0",
            source = "NEU JWXT /jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do",
            generatedAt = generatedAt,
            account = account,
            term = JwxtNamedCode(termCode, termName),
            campus = JwxtNamedCode(campusCode, campusName),
            summary = JwxtScheduleSummary(
                courseCount = courses.size,
                eventCount = events.size,
                notArrangedCount = notArranged.size(),
                practiceCount = practice.size()
            ),
            sections = normalizedSections,
            courses = courses,
            events = events,
            notArrangedCount = notArranged.size(),
            practiceCount = practice.size()
        )
    }

    private fun weekSpec(raw: String): String {
        val prefix = raw.split("/", limit = 2).first()
        return prefix.replace(Regex("""\[[^\]]*\]"""), "").trim()
    }

    private fun teachers(raw: String): List<String> {
        val parts = raw.split("/", limit = 2)
        if (parts.size < 2) return emptyList()
        return Regex("""(?:^|[,，])\s*([^,，\[\]/]+?)\s*\[[^\]]+\]""")
            .findAll(parts[1])
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun eventId(event: JwxtScheduleEvent): String {
        val identity = buildString {
            append("courseCode=").append(event.courseCode)
            append(";teachingClassId=").append(event.teachingClassId)
            append(";weekday=").append(event.weekday)
            append(";startSection=").append(event.sections.start)
            append(";endSection=").append(event.sections.end)
            append(";weekSpec=").append(event.weekSpec)
            append(";classroom=").append(event.classroom)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray {
        val value = get(name)
        return if (value != null && value.isJsonArray) value.asJsonArray else JsonArray()
    }

    private fun JsonObject.stringOrEmpty(name: String): String {
        val value = get(name) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    private fun JsonObject.intOrZero(name: String): Int {
        val value = get(name) ?: return 0
        if (value.isJsonNull) return 0
        return try {
            value.asInt
        } catch (_: Exception) {
            value.asString.toIntOrNull() ?: 0
        }
    }

    private fun JsonObject.numberOrNull(name: String): Double? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return try {
            value.asDouble
        } catch (_: Exception) {
            value.asString.toDoubleOrNull()
        }
    }

    private fun JsonObject.stringList(name: String): List<String> {
        val value = get(name) ?: return emptyList()
        if (!value.isJsonArray) return emptyList()
        return value.asJsonArray.mapNotNull { element ->
            if (element.isJsonPrimitive) element.asString else null
        }
    }
}
