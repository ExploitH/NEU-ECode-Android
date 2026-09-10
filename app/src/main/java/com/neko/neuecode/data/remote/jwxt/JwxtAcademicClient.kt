package com.neko.neuecode.data.remote.jwxt

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.neko.neuecode.data.remote.NeuCampusHttp
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class JwxtAcademicClient(
    private val http: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://jwxt.neu.edu.cn"
        const val HOME_SERVICE = JwxtScheduleClient.HOME_SERVICE
        const val HOME_REFERER =
            "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/home/index.html?av=&contextPath=/jwapp"
        const val SCORES_PATH = "/jwapp/sys/homeapp/api/home/student/scores.do"
        const val EXAMS_PATH = "/jwapp/sys/homeapp/api/home/student/exams.do"
    }

    fun getScores(termCode: String): JsonArray = postStudentList(SCORES_PATH, termCode, "scores")

    fun getExams(termCode: String): JsonArray = postStudentList(EXAMS_PATH, termCode, "exams")

    private fun postStudentList(path: String, termCode: String, model: String): JsonArray {
        var lastError: JwxtProtocolException? = null
        repeat(3) { attempt ->
            try {
                return postStudentListOnce(path, termCode, model)
            } catch (error: JwxtProtocolException) {
                lastError = error
                if (!NeuCampusHttp.isRetryableJwxtModuleStatus(error) &&
                    !NeuCampusHttp.looksLikeCampusTransport(error.message.orEmpty())
                ) {
                    throw error
                }
                if (attempt == 2) throw error
            }
        }
        throw lastError ?: JwxtProtocolException("JWXT $model failed")
    }

    private fun postStudentListOnce(path: String, termCode: String, model: String): JsonArray {
        val body = FormBody.Builder().add("termCode", termCode).build()
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", HOME_REFERER)
            .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (NeuCampusHttp.isRetryableJwxtModule(response.code)) {
                throw JwxtProtocolException("JWXT $model failed: HTTP ${response.code}")
            }
            if (!response.isSuccessful) {
                throw JwxtProtocolException("JWXT $model failed: HTTP ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            if (text.contains("<html", ignoreCase = true) || text.contains("loginForm")) {
                throw JwxtProtocolException("JWXT $model returned an HTML login page")
            }
            val payload = JsonParser.parseString(text).asJsonObject
            if (payload.get("code")?.asString != "0") {
                val message = payload.get("msg")?.asString ?: "non-success"
                throw JwxtProtocolException("JWXT $model failed: $message")
            }
            val datas = payload.get("datas")
                ?: throw JwxtProtocolException("JWXT $model omitted datas")
            if (datas.isJsonNull) return JsonArray()
            if (!datas.isJsonArray) {
                throw JwxtProtocolException("JWXT $model datas is not a list")
            }
            return datas.asJsonArray
        }
    }
}
