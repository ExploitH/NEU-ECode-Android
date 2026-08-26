package com.neko.neuecode.data.remote.enrollment

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.neko.neuecode.domain.enrollment.EnrollmentCatalogPage
import com.neko.neuecode.domain.enrollment.EnrollmentCourse
import com.neko.neuecode.domain.enrollment.EnrollmentSchedule
import com.neko.neuecode.domain.enrollment.EnrollmentScheduleEntry
import com.neko.neuecode.domain.enrollment.EnrollmentSection
import com.neko.neuecode.domain.enrollment.EnrollmentSelectedCourse

internal object EnrollmentPayloadParser {
    fun parseSchedule(raw: String): EnrollmentSchedule {
        val data = successfulData(raw, EnrollmentReadEndpoint.SCHEDULE).asObject("课表 data")
        val sections = data.objectOrNull("sectionMap")
            ?.entrySet()
            ?.associate { (code, element) ->
                val item = element.asObject("sectionMap.$code")
                code to EnrollmentSection(
                    code = item.string("sectionCode").ifBlank { code },
                    name = item.string("sectionName"),
                    beginTime = item.string("beginTime"),
                    endTime = item.string("endTime")
                )
            }
            .orEmpty()
        val entries = data.arrayOrEmpty("scheduleList").mapNotNull { element ->
            val item = element.asJsonObject
            val teachingClassId = item.string("JXBID")
            if (teachingClassId.isBlank()) return@mapNotNull null
            EnrollmentScheduleEntry(
                teachingClassId = teachingClassId,
                courseCode = item.string("KCH"),
                courseName = item.string("KCM"),
                teacher = item.string("SKJS"),
                weekday = item.int("SKXQ"),
                startSection = item.int("KSJC"),
                endSection = item.int("JSJC"),
                weeks = item.string("SKZCMC").ifBlank { item.string("SKZC") }
            )
        }
        return EnrollmentSchedule(
            termName = data.string("schoolTermName"),
            sections = sections,
            entries = entries
        )
    }

    fun parseCatalog(
        raw: String,
        clazzType: String,
        pageNumber: Int,
        pageSize: Int
    ): EnrollmentCatalogPage {
        val data = successfulData(raw, EnrollmentReadEndpoint.CATALOG).asObject("课程列表 data")
        val courses = mutableListOf<EnrollmentCourse>()
        val rows = data.arrayOrEmpty("rows")
        rows.forEach { row ->
            collectCatalog(row, CatalogContext(clazzType = clazzType), courses)
        }
        val unique = courses.distinctBy { it.clazzId }
        val declaredTotal = data.intOrNull("total")?.takeIf { it >= 0 }
        val hasMore = declaredTotal?.let { pageNumber * pageSize < it }
            ?: (rows.size() >= pageSize)
        val inferredTotal = (pageNumber - 1) * pageSize + rows.size() + if (hasMore) 1 else 0
        return EnrollmentCatalogPage(
            courses = unique,
            pageNumber = pageNumber,
            pageSize = pageSize,
            total = declaredTotal ?: inferredTotal,
            hasMore = hasMore
        )
    }

    fun parseSelected(
        raw: String,
        endpoint: EnrollmentReadEndpoint,
        sourceLabel: String
    ): List<EnrollmentSelectedCourse> {
        val output = mutableListOf<EnrollmentSelectedCourse>()
        collectSelected(successfulData(raw, endpoint), SelectedContext(sourceLabel = sourceLabel), output)
        return output.distinctBy { it.teachingClassId }
    }

    private fun successfulData(raw: String, endpoint: EnrollmentReadEndpoint): JsonElement {
        val root = try {
            JsonParser.parseString(raw).asJsonObject
        } catch (error: Exception) {
            throw EnrollmentProtocolException(endpoint, "选课接口返回了无法解析的 JSON", error)
        }
        val code = root.int("code")
        if (code != 200) {
            val message = root.string("msg").ifBlank { "选课接口业务码 ${root.string("code")}" }
            if (code == 401 || code == 403 || SESSION_MESSAGE_PATTERN.containsMatchIn(message)) {
                throw EnrollmentSessionExpiredException(endpoint)
            }
            throw EnrollmentProtocolException(
                endpoint,
                message
            )
        }
        return root.get("data") ?: JsonObject()
    }

    private data class CatalogContext(
        val courseCode: String = "",
        val courseName: String = "",
        val teacher: String = "",
        val credits: Double = 0.0,
        val clazzType: String = "",
        val clazzTypeLabel: String = "",
        val selectedCount: Int = 0,
        val capacity: Int = 0
    )

    private fun collectCatalog(
        element: JsonElement,
        inherited: CatalogContext,
        output: MutableList<EnrollmentCourse>
    ) {
        when {
            element.isJsonArray -> element.asJsonArray.forEach { collectCatalog(it, inherited, output) }
            !element.isJsonObject -> Unit
            else -> {
                val item = element.asJsonObject
                val context = inherited.copy(
                    courseCode = item.firstString("KCH", "courseCode").ifBlank { inherited.courseCode },
                    courseName = item.firstString("KCM", "courseName", "kcName").ifBlank { inherited.courseName },
                    teacher = item.firstString("SKJS", "teacherName", "JSXM").ifBlank { inherited.teacher },
                    credits = item.firstDoubleOrNull("XF", "credit") ?: inherited.credits,
                    clazzType = item.firstString("teachingClassType", "clazzType").ifBlank { inherited.clazzType },
                    clazzTypeLabel = item.firstString("teachingClassTypeName", "clazzTypeName", "KCLB").ifBlank { inherited.clazzTypeLabel },
                    selectedCount = item.firstIntOrNull("QZXKRS", "YXRS", "numberOfSelected") ?: inherited.selectedCount,
                    capacity = item.firstIntOrNull("KRL", "classCapacity") ?: inherited.capacity
                )
                val teachingClassId = item.firstString("JXBID", "clazzId", "teachingClassID")
                if (teachingClassId.isNotBlank()) {
                    output += EnrollmentCourse(
                        id = teachingClassId,
                        name = context.courseName.ifBlank { "未命名课程" },
                        teacher = context.teacher,
                        clazzType = context.clazzType,
                        clazzTypeLabel = context.clazzTypeLabel.ifBlank { context.clazzType },
                        clazzId = teachingClassId,
                        credits = context.credits,
                        selectedCount = context.selectedCount,
                        capacity = context.capacity
                    )
                }
                item.entrySet().forEach { (key, child) ->
                    if (key != "secretVal") collectCatalog(child, context, output)
                }
            }
        }
    }

    private data class SelectedContext(
        val courseCode: String = "",
        val courseName: String = "",
        val teacher: String = "",
        val clazzType: String = "",
        val credits: Double = 0.0,
        val selectedCount: Int = 0,
        val capacity: Int = 0,
        val sourceLabel: String
    )

    private fun collectSelected(
        element: JsonElement,
        inherited: SelectedContext,
        output: MutableList<EnrollmentSelectedCourse>
    ) {
        when {
            element.isJsonArray -> element.asJsonArray.forEach { collectSelected(it, inherited, output) }
            !element.isJsonObject -> Unit
            else -> {
                val item = element.asJsonObject
                val context = inherited.copy(
                    courseCode = item.firstString("KCH", "courseCode").ifBlank { inherited.courseCode },
                    courseName = item.firstString("KCM", "courseName").ifBlank { inherited.courseName },
                    teacher = item.firstString("SKJS", "teacherName").ifBlank { inherited.teacher },
                    clazzType = item.firstString("teachingClassType", "clazzType").ifBlank { inherited.clazzType },
                    credits = item.firstDoubleOrNull("XF", "credit") ?: inherited.credits,
                    selectedCount = item.firstIntOrNull("QZXKRS", "YXRS", "numberOfSelected") ?: inherited.selectedCount,
                    capacity = item.firstIntOrNull("KRL", "classCapacity") ?: inherited.capacity
                )
                val teachingClassId = item.firstString("JXBID", "clazzId", "teachingClassID")
                if (teachingClassId.isNotBlank()) {
                    output += EnrollmentSelectedCourse(
                        teachingClassId = teachingClassId,
                        courseCode = context.courseCode,
                        courseName = context.courseName.ifBlank { "未命名课程" },
                        teacher = context.teacher,
                        clazzType = context.clazzType,
                        sourceLabel = context.sourceLabel,
                        credits = context.credits,
                        selectedCount = context.selectedCount,
                        capacity = context.capacity,
                        currentWeight = item.intOrNull("TRQZ")
                    )
                }
                item.entrySet().forEach { (key, child) ->
                    if (key != "secretVal") collectSelected(child, context, output)
                }
            }
        }
    }

    private fun JsonElement.asObject(label: String): JsonObject =
        takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("$label 不是对象")

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.int(name: String): Int = intOrNull(name) ?: 0

    private fun JsonObject.intOrNull(name: String): Int? =
        runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toInt() }.getOrNull()

    private fun JsonObject.firstString(vararg names: String): String =
        names.firstNotNullOfOrNull { name -> string(name).takeIf { it.isNotBlank() } }.orEmpty()

    private fun JsonObject.firstIntOrNull(vararg names: String): Int? =
        names.firstNotNullOfOrNull { name -> intOrNull(name) }

    private fun JsonObject.firstDoubleOrNull(vararg names: String): Double? =
        names.firstNotNullOfOrNull { name ->
            runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() }.getOrNull()
        }

    private val SESSION_MESSAGE_PATTERN =
        Regex("登录|会话|token|authorization|未认证|重新进入", RegexOption.IGNORE_CASE)
}

class EnrollmentProtocolException(
    endpoint: EnrollmentReadEndpoint,
    message: String,
    cause: Throwable? = null
) : EnrollmentTransportException(endpoint, message, cause)
