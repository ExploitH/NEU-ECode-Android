package com.neko.neuecode.data.remote.jwxt

import com.neko.neuecode.data.remote.NeuCampusHttp
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class CasSecondAuthSendResult(
    val ok: Boolean,
    val message: String,
)

data class CasSecondAuthSubmitResult(
    val ok: Boolean,
    val message: String,
    val finalUrl: String,
)

@Singleton
class CasSecondAuthClient @Inject constructor(
    sharedHttp: OkHttpClient,
) {
    private val http: OkHttpClient = sharedHttp.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun refreshCaptchaUrl(): String {
        val stamp = System.currentTimeMillis()
        return "https://pass.neu.edu.cn/tpass/code?$stamp"
    }

    fun sendSmsCode(
        pageUrl: String,
        graphicCaptcha: String,
    ): CasSecondAuthSendResult {
        val requestSpec = CasSecondAuthParser.sendCodeRequest(pageUrl, graphicCaptcha)
        val form = FormBody.Builder().apply {
            requestSpec.fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val response = http.newCall(
            Request.Builder()
                .url(requestSpec.url)
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", pageUrl)
                .post(form)
                .build(),
        ).execute()
        val body = response.use { it.body?.string().orEmpty() }
        return parseSendResult(response.isSuccessful, body)
    }

    fun submitSmsCode(
        challenge: CasSecondAuthChallenge,
        smsCode: String,
    ): CasSecondAuthSubmitResult {
        val requestSpec = CasSecondAuthParser.submitSmsRequest(challenge, smsCode)
        val form = FormBody.Builder().apply {
            requestSpec.fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val response = http.newCall(
            Request.Builder()
                .url(requestSpec.url)
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .header("Referer", challenge.formAction.ifBlank { "https://pass.neu.edu.cn/tpass/login" })
                .post(form)
                .build(),
        ).execute()
        val url = response.request.url
        val body = response.use { it.body?.string().orEmpty() }
        if (CasSecondAuthParser.isChallengeHtml(body) || JwxtCasCrypto.looksLikeSmsChallenge(body)) {
            return CasSecondAuthSubmitResult(
                ok = false,
                message = "短信验证码不正确或已过期，请重新获取",
                finalUrl = url.toString(),
            )
        }
        val stillOnLogin = url.host.equals("pass.neu.edu.cn", ignoreCase = true) &&
            url.encodedPath.startsWith("/tpass/login")
        return CasSecondAuthSubmitResult(
            ok = response.isSuccessful && !stillOnLogin,
            message = if (stillOnLogin) "二次验证未完成" else "验证成功",
            finalUrl = url.toString(),
        )
    }

    private fun parseSendResult(httpOk: Boolean, body: String): CasSecondAuthSendResult {
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (json != null) {
            val success = json.optBoolean("success", json.optString("info") == "ok")
            val message = json.optString("message").ifBlank {
                json.optString("info").ifBlank {
                    if (success) "验证码发送成功，请注意查收！" else "验证码发送失败"
                }
            }
            return CasSecondAuthSendResult(ok = success, message = message)
        }
        Timber.w("secondAuthCode returned non-JSON body len=${body.length}")
        return CasSecondAuthSendResult(
            ok = false,
            message = if (httpOk) "验证码发送失败" else "请求失败，请稍后重试",
        )
    }
}
