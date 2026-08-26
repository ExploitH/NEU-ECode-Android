package com.neko.neuecode.data.remote.enrollment

import javax.inject.Inject
import javax.inject.Singleton

/** Process-only state captured from an authenticated JWXK page. */
data class EnrollmentPortalSession(
    val headers: EnrollmentSessionHeaders,
    val batchName: String,
    val typeCode: String,
    val campus: String,
    val courseTypes: List<String>
)

@Singleton
class EnrollmentSessionStore @Inject constructor() {
    @Volatile
    private var session: EnrollmentPortalSession? = null

    fun replace(value: EnrollmentPortalSession) {
        session = value.copy(courseTypes = value.courseTypes.distinct())
    }

    fun current(): EnrollmentPortalSession? = session

    fun clear() {
        session = null
    }
}