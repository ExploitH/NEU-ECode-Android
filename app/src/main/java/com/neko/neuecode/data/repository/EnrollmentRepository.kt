package com.neko.neuecode.data.repository

import com.neko.neuecode.data.remote.enrollment.EnrollmentPayloadParser
import com.neko.neuecode.data.remote.enrollment.EnrollmentPortalSession
import com.neko.neuecode.data.remote.enrollment.EnrollmentReadEndpoint
import com.neko.neuecode.data.remote.enrollment.EnrollmentRequestBody
import com.neko.neuecode.data.remote.enrollment.EnrollmentSessionExpiredException
import com.neko.neuecode.data.remote.enrollment.EnrollmentSessionStore
import com.neko.neuecode.data.remote.enrollment.EnrollmentSessionUnavailableException
import com.neko.neuecode.data.remote.enrollment.SerializedEnrollmentTransport
import com.neko.neuecode.domain.enrollment.EnrollmentCatalogPage
import com.neko.neuecode.domain.enrollment.EnrollmentSchedule
import com.neko.neuecode.domain.enrollment.EnrollmentSelectedCourse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnrollmentRepository @Inject constructor(
    private val transport: SerializedEnrollmentTransport,
    private val sessionStore: EnrollmentSessionStore
) {
    fun sessionMetadata(): EnrollmentSessionMetadata? = sessionStore.current()?.let { session ->
        EnrollmentSessionMetadata(
            batchName = session.batchName,
            typeCode = session.typeCode,
            campus = session.campus,
            courseTypes = session.courseTypes
        )
    }

    fun hasSession(): Boolean = sessionStore.current() != null

    fun clearSession() = sessionStore.clear()

    suspend fun loadSchedule(): EnrollmentSchedule {
        val session = requireSession()
        val fields = session.campus.takeIf { it.isNotBlank() }
            ?.let { mapOf("campus" to it) }
            .orEmpty()
        val raw = transport.postReadOnly(
            endpoint = EnrollmentReadEndpoint.SCHEDULE,
            session = session.headers,
            body = EnrollmentRequestBody.Form(fields)
        )
        return EnrollmentPayloadParser.parseSchedule(raw)
    }

    suspend fun loadCatalogPage(
        clazzType: String,
        pageNumber: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): EnrollmentCatalogPage {
        require(pageNumber > 0)
        require(pageSize in 1..MAX_PAGE_SIZE)
        val session = requireSession()
        val fields = linkedMapOf<String, Any?>(
            "teachingClassType" to clazzType,
            "pageNumber" to pageNumber,
            "pageSize" to pageSize,
            "orderBy" to ""
        )
        if (clazzType != ALL_COURSES && session.campus.isNotBlank()) {
            fields["campus"] = session.campus
        }
        val raw = transport.postReadOnly(
            endpoint = EnrollmentReadEndpoint.CATALOG,
            session = session.headers,
            body = EnrollmentRequestBody.Json(fields)
        )
        return EnrollmentPayloadParser.parseCatalog(raw, clazzType, pageNumber, pageSize)
    }

    suspend fun loadSelectedCourses(): EnrollmentSelectedResult {
        val session = requireSession()
        val courses = mutableListOf<EnrollmentSelectedCourse>()
        val failures = mutableListOf<String>()
        SELECTED_SOURCES.forEach { source ->
            try {
                val raw = transport.postReadOnly(
                    endpoint = source.endpoint,
                    session = session.headers,
                    body = EnrollmentRequestBody.Json()
                )
                courses += EnrollmentPayloadParser.parseSelected(raw, source.endpoint, source.label)
            } catch (error: EnrollmentSessionExpiredException) {
                throw error
            } catch (_: Exception) {
                failures += "${source.label}读取失败"
            }
        }
        return EnrollmentSelectedResult(
            courses = courses.distinctBy { it.teachingClassId },
            failures = failures
        )
    }

    private fun requireSession(): EnrollmentPortalSession = sessionStore.current()
        ?: throw EnrollmentSessionUnavailableException("请先打开选课官网并同步当前批次")

    companion object {
        const val ALL_COURSES = "ALLKC"
        const val DEFAULT_PAGE_SIZE = 100
        const val MAX_PAGE_SIZE = 100

        private data class SelectedSource(
            val endpoint: EnrollmentReadEndpoint,
            val label: String
        )

        private val SELECTED_SOURCES = listOf(
            SelectedSource(EnrollmentReadEndpoint.VOLUNTEER_SELECTED, "方案/推荐课"),
            SelectedSource(EnrollmentReadEndpoint.GENERAL_SELECTED, "校公选课"),
            SelectedSource(EnrollmentReadEndpoint.ALL_SELECTED, "全部已选")
        )
    }
}

data class EnrollmentSessionMetadata(
    val batchName: String,
    val typeCode: String,
    val campus: String,
    val courseTypes: List<String>
)

data class EnrollmentSelectedResult(
    val courses: List<EnrollmentSelectedCourse>,
    val failures: List<String>
)