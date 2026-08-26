package com.neko.neuecode.data.remote.ecode

import okhttp3.OkHttpClient
import okhttp3.Request

data class ECodePayCodeHttpResponse(
    val code: Int,
    val contentType: String?,
    val body: String,
)

/**
 * Thin OkHttp GET of `/ecode/api/qr-code`. Session cookies come from the
 * injected client's CookieJar. Tests inject [MockWebServer] via [baseUrl].
 */
class ECodePayCodeApi(
    private val http: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://ecode.neu.edu.cn"
        const val QR_CODE_PATH = "/ecode/api/qr-code"
    }

    fun getQrCode(): ECodePayCodeHttpResponse {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + QR_CODE_PATH)
            .get()
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return ECodePayCodeHttpResponse(
                code = response.code,
                contentType = response.header("Content-Type"),
                body = body,
            )
        }
    }
}
