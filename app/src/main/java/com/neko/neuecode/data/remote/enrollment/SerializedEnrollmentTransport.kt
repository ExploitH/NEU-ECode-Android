package com.neko.neuecode.data.remote.enrollment

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Serializes JWXK requests so a response's rotating JSESSIONID is committed to
 * OkHttp's CookieJar before the next request is allowed to build its Cookie header.
 */
class SerializedEnrollmentTransport(
    private val client: OkHttpClient,
    private val baseUrl: String = JWXK_BASE_URL,
    private val minRequestIntervalMs: Long = DEFAULT_MIN_REQUEST_INTERVAL_MS
) {
    private val requestMutex = Mutex()
    private var lastRequestStartedNanos = 0L

    suspend fun postReadOnly(
        endpoint: EnrollmentReadEndpoint,
        session: EnrollmentSessionHeaders,
        body: EnrollmentRequestBody = EnrollmentRequestBody.Json()
    ): String = requestMutex.withLock {
        paceRequests()
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + endpoint.path)
                .header("Accept", "application/json, text/plain, */*")
                .header("Authorization", session.authorization)
                .header("batchId", session.batchId)
                .post(body.toRequestBody())
                .build()

            client.newCall(request).execute().use { response ->
                validateResponse(endpoint, response)
                response.body?.string() ?: throw EnrollmentTransportException(
                    endpoint = endpoint,
                    message = "选课接口响应为空"
                )
            }
        }
    }

    private fun validateResponse(endpoint: EnrollmentReadEndpoint, response: Response) {
        if (response.isRedirect || response.code == 401 || response.code == 403) {
            throw EnrollmentSessionExpiredException(endpoint)
        }
        if (!response.isSuccessful) {
            throw EnrollmentTransportException(
                endpoint = endpoint,
                message = "选课接口 HTTP ${response.code}"
            )
        }
        val contentType = response.header("Content-Type").orEmpty()
        if (!contentType.contains("application/json", ignoreCase = true)) {
            throw EnrollmentSessionExpiredException(endpoint)
        }
    }

    companion object {
        const val JWXK_BASE_URL = "https://jwxk.neu.edu.cn/xsxk"
        const val DEFAULT_MIN_REQUEST_INTERVAL_MS = 500L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private suspend fun paceRequests() {
        if (minRequestIntervalMs <= 0L) return
        val elapsedMs = (System.nanoTime() - lastRequestStartedNanos) / 1_000_000L
        if (lastRequestStartedNanos != 0L && elapsedMs < minRequestIntervalMs) {
            delay(minRequestIntervalMs - elapsedMs)
        }
        lastRequestStartedNanos = System.nanoTime()
    }

    private fun EnrollmentRequestBody.toRequestBody(): RequestBody = when (this) {
        is EnrollmentRequestBody.Form -> FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()

        is EnrollmentRequestBody.Json -> Gson().toJson(fields).toRequestBody(JSON_MEDIA_TYPE)
    }
}

sealed interface EnrollmentRequestBody {
    data class Form(val fields: Map<String, String> = emptyMap()) : EnrollmentRequestBody
    data class Json(val fields: Map<String, Any?> = emptyMap()) : EnrollmentRequestBody
}

data class EnrollmentSessionHeaders(
    val authorization: String,
    val batchId: String
) {
    init {
        require(authorization.isNotBlank()) { "Authorization must not be blank" }
        require(batchId.isNotBlank()) { "batchId must not be blank" }
    }
}

enum class EnrollmentReadEndpoint(val path: String) {
    SCHEDULE("/elective/neu/xskb"),
    CATALOG("/elective/clazz/list"),
    VOLUNTEER_SELECTED("/volunteer/select"),
    GENERAL_SELECTED("/volunteer/xgxk/select"),
    ALL_SELECTED("/elective/select")
}

open class EnrollmentTransportException(
    val endpoint: EnrollmentReadEndpoint,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class EnrollmentSessionExpiredException(endpoint: EnrollmentReadEndpoint) :
    EnrollmentTransportException(endpoint, "选课登录态已失效，请重新进入选课系统")

class EnrollmentSessionUnavailableException(message: String) : IOException(message)
