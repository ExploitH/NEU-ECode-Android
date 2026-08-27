package com.neko.neuecode.data.remote.jwxt

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.neko.neuecode.data.remote.NeuCampusHttp
import com.neko.neuecode.domain.jwxt.JwxtNamedCode
import com.neko.neuecode.domain.jwxt.ScheduleSyncProgress
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class JwxtProtocolException(message: String) : IOException(message)

data class JwxtScheduleBundle(
    val term: JwxtNamedCode,
    val campus: JwxtNamedCode,
    val sections: List<JsonObject>,
    val schedule: JsonObject
)

class JwxtScheduleClient(
    private val http: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://jwxt.neu.edu.cn"
        const val HOME_SERVICE = "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do"
        const val SCHEDULE_REFERER = "https://jwxt.neu.edu.cn/jwapp/sys/kbapp/*default/index.do#/wdkb"
        const val KBAPP_INDEX_PATH = "/jwapp/sys/kbapp/*default/index.do"
    }

    private val followHttp: OkHttpClient = http.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getCurrentTerm(): JwxtNamedCode {
        val model = postModel(
            "/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do",
            mapOf("CSDM" to "SYS", "ZCSDM" to "DQXNXQDM", "SFSY" to "1"),
            "cxmrxnxq"
        ).asJsonObject
        val rows = model.getAsJsonArray("rows") ?: throw JwxtProtocolException("JWXT current-term model has no rows")
        if (rows.size() == 0) throw JwxtProtocolException("JWXT current-term model has no rows")
        val row = rows[0].asJsonObject
        val code = row.stringOrEmpty("XNXQDM")
        if (code.isBlank()) throw JwxtProtocolException("JWXT current-term row omitted XNXQDM")
        return JwxtNamedCode(code, row.stringOrEmpty("XNXQMC"))
    }

    fun listTerms(): List<JwxtNamedCode> {
        val model = postModel(
            "/jwapp/sys/jwpubapp/modules/zdgl/xnxqcx.do",
            mapOf("*order" to "+DM"),
            "xnxqcx"
        ).asJsonObject
        val rows = model.getAsJsonArray("rows") ?: throw JwxtProtocolException("JWXT term-list model has no rows")
        return rows.mapNotNull { element ->
            val row = element.asJsonObject
            val code = row.stringOrEmpty("DM").ifBlank { row.stringOrEmpty("XNXQDM") }
            if (code.isBlank()) return@mapNotNull null
            val name = row.stringOrEmpty("MC").ifBlank { row.stringOrEmpty("XNXQMC") }
            JwxtNamedCode(code, name)
        }
    }

    fun getCampuses(termCode: String): JsonArray {
        warmupKbappSession()
        val model = postModel(
            "/jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do",
            mapOf("XNXQDM" to termCode),
            "getMyScheduledCampus"
        )
        if (!model.isJsonArray) throw JwxtProtocolException("JWXT campus model is not a list")
        return model.asJsonArray
    }

    fun getSections(termCode: String, campusCode: String): List<JsonObject> {
        val model = postModel(
            "/jwapp/sys/kbapp/api/wdkbcx/getMySectionList.do",
            mapOf("XNXQDM" to termCode, "XQDM" to campusCode),
            "getMySectionList"
        )
        if (!model.isJsonArray) throw JwxtProtocolException("JWXT section model is not a list")
        return model.asJsonArray.map { it.asJsonObject }
    }

    fun getSchedule(termCode: String, campusCode: String): JsonObject {
        val model = postModel(
            "/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do",
            mapOf("XNXQDM" to termCode, "XQDM" to campusCode),
            "getMyScheduleDetail"
        )
        if (!model.isJsonObject || !model.asJsonObject.has("arrangedList")) {
            throw JwxtProtocolException("JWXT schedule model has an unexpected shape")
        }
        return model.asJsonObject
    }

    fun fetchBundle(
        termCode: String? = null,
        termName: String = "",
        campusCode: String? = null,
        onProgress: (ScheduleSyncProgress) -> Unit = {},
    ): JwxtScheduleBundle {
        onProgress(ScheduleSyncProgress.currentTerm())
        val term = if (termCode.isNullOrBlank()) {
            getCurrentTerm()
        } else {
            JwxtNamedCode(termCode, termName)
        }
        onProgress(ScheduleSyncProgress.campuses())
        val campuses = getCampuses(term.code)
        if (campuses.size() == 0) {
            throw JwxtProtocolException("JWXT returned no campus for term ${term.code}")
        }
        val selected = if (campusCode == null) {
            campuses[0].asJsonObject
        } else {
            campuses.firstOrNull { it.asJsonObject.stringOrEmpty("id") == campusCode }?.asJsonObject
                ?: throw JwxtProtocolException("campus $campusCode is unavailable for term ${term.code}")
        }
        val campus = JwxtNamedCode(
            code = selected.stringOrEmpty("id"),
            name = selected.stringOrEmpty("name")
        )
        if (campus.code.isBlank()) throw JwxtProtocolException("selected campus omitted its id")
        onProgress(ScheduleSyncProgress.sections())
        val sections = getSections(term.code, campus.code)
        onProgress(ScheduleSyncProgress.details())
        return JwxtScheduleBundle(
            term = term,
            campus = campus,
            sections = sections,
            schedule = getSchedule(term.code, campus.code)
        )
    }

    private fun postModel(path: String, fields: Map<String, String>, model: String): com.google.gson.JsonElement {
        var lastError: JwxtProtocolException? = null
        repeat(3) { attempt ->
            try {
                return postModelOnce(path, fields, model)
            } catch (error: JwxtProtocolException) {
                lastError = error
                if (!NeuCampusHttp.isRetryableJwxtModuleStatus(error) &&
                    !NeuCampusHttp.looksLikeCampusTransport(error.message.orEmpty())
                ) {
                    throw error
                }
                if (attempt == 2) throw error
                if (error.message.orEmpty().contains("HTTP 403")) {
                    warmupKbappSession()
                }
            }
        }
        throw lastError ?: JwxtProtocolException("JWXT model $model failed")
    }

    private fun warmupKbappSession() {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + KBAPP_INDEX_PATH)
            .get()
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", HOME_SERVICE)
            .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
            .build()
        runCatching {
            followHttp.newCall(request).execute().use { it.body?.string() }
        }
    }

    private fun postModelOnce(path: String, fields: Map<String, String>, model: String): com.google.gson.JsonElement {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", SCHEDULE_REFERER)
            .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (NeuCampusHttp.isRetryableJwxtModule(response.code)) {
                throw JwxtProtocolException("JWXT model $model failed: HTTP ${response.code}")
            }
            if (!response.isSuccessful) {
                throw JwxtProtocolException("JWXT model $model failed: HTTP ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            if (text.contains("<html", ignoreCase = true) || text.contains("loginForm")) {
                throw JwxtProtocolException("JWXT model $model returned an HTML login page")
            }
            val payload = JsonParser.parseString(text).asJsonObject
            if (payload.get("code")?.asString != "0") {
                val message = payload.get("msg")?.asString ?: "non-success"
                throw JwxtProtocolException("JWXT model $model failed: $message")
            }
            val datas = payload.getAsJsonObject("datas")
                ?: throw JwxtProtocolException("JWXT response omitted model $model")
            return datas.get(model) ?: throw JwxtProtocolException("JWXT response omitted model $model")
        }
    }
}

private fun JsonObject.stringOrEmpty(name: String): String {
    val value = get(name) ?: return ""
    return if (value.isJsonNull) "" else value.asString
}
