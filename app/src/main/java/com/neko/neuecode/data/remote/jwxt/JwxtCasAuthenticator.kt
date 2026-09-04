package com.neko.neuecode.data.remote.jwxt

import com.neko.neuecode.data.remote.NeuCampusHttp
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

open class JwxtAuthenticationException(message: String) : IOException(message)
class JwxtHumanVerificationRequired(
    message: String,
    val challenge: CasSecondAuthChallenge? = null,
) : JwxtAuthenticationException(message)

data class JwxtCasLoginResult(
    val ok: Boolean,
    val account: String,
    val finalUrl: String
)

class JwxtCasAuthenticator(
    sharedHttp: OkHttpClient,
    private val loginEndpoint: String = DEFAULT_LOGIN_ENDPOINT
) {
    companion object {
        const val DEFAULT_LOGIN_ENDPOINT = "https://pass.neu.edu.cn/tpass/login"
        private val mfaPattern = Regex("登录码已发送|输入验证码|手机验证码|动态验证码|二次认证|图形验证码|当前设备需进行身份验证|绑定手机尾号")
        private val failurePattern = Regex("账号或密码错误|登录失败|认证失败|剩余.{0,20}次")
    }

    private val http: OkHttpClient = sharedHttp.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun login(username: String, password: String, service: String): JwxtCasLoginResult {
        val serviceUrl = service.toHttpUrl()
        if (serviceUrl.scheme != "https" || !serviceUrl.host.endsWith("neu.edu.cn")) {
            throw JwxtAuthenticationException("CAS service must be an HTTPS neu.edu.cn URL")
        }
        val loginUrl = loginEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("service", service)
            .build()
        val page = http.newCall(
            Request.Builder()
                .url(loginUrl)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .get()
                .build()
        ).execute()
        val pageUrl = page.request.url
        val pageCode = page.code
        val pageBody = page.use { it.body?.string().orEmpty() }
        if (NeuCampusHttp.casAlreadyAuthenticated(
                host = pageUrl.host,
                path = pageUrl.encodedPath,
                code = pageCode,
                body = pageBody,
            )
        ) {
            return JwxtCasLoginResult(
                ok = true,
                account = username,
                finalUrl = pageUrl.newBuilder().query(null).fragment(null).build().toString(),
            )
        }
        if (pageCode !in 200..299) {
            throw JwxtAuthenticationException("CAS login page failed: HTTP $pageCode")
        }
        if (JwxtCasCrypto.looksLikeSmsChallenge(pageBody)) {
            throw JwxtHumanVerificationRequired(
                "CAS requires live SMS/CAPTCHA/device verification",
                CasSecondAuthParser.parse(pageBody, pageUrl.toString()),
            )
        }
        val scriptUrl = JwxtCasCrypto.extractLoginScriptUrl(pageBody, pageUrl.toString())
            ?: throw JwxtAuthenticationException("CAS RSA public key is unavailable")
        val js = http.newCall(
            Request.Builder()
                .url(scriptUrl)
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .get()
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw JwxtAuthenticationException("CAS RSA public key is unavailable")
            response.body?.string().orEmpty()
        }
        val publicKey = JwxtCasCrypto.extractPublicKeyFromJs(js)
        val submission = JwxtCasCrypto.buildLoginSubmission(
            pageHtml = pageBody,
            pageUrl = pageUrl.toString(),
            publicKeyB64 = publicKey,
            username = username,
            password = password
        )
        val form = FormBody.Builder().apply {
            submission.fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val result = http.newCall(
            Request.Builder()
                .url(submission.action)
                .header("Referer", page.request.url.toString())
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .post(form)
                .build()
        ).execute()
        val resultUrl = result.request.url
        val resultCode = result.code
        val resultBody = result.use { it.body?.string().orEmpty() }
        val finalHost = resultUrl.host
        if (NeuCampusHttp.isEcodeIntermediateLanding(resultCode, finalHost)) {
            return JwxtCasLoginResult(
                ok = true,
                account = username,
                finalUrl = resultUrl.newBuilder().query(null).fragment(null).build().toString(),
            )
        }
        if (!result.isSuccessful) {
            throw JwxtAuthenticationException("CAS password login did not complete")
        }
        if (mfaPattern.containsMatchIn(resultBody) || CasSecondAuthParser.isChallengeHtml(resultBody)) {
            throw JwxtHumanVerificationRequired(
                "CAS requires live SMS/CAPTCHA/device verification",
                CasSecondAuthParser.parse(resultBody, resultUrl.toString()),
            )
        }
        val stillHasForm = resultBody.contains("id=\"loginForm\"", ignoreCase = true) ||
            resultBody.contains("id='loginForm'", ignoreCase = true)
        val stillOnLogin = finalHost == "pass.neu.edu.cn" && resultUrl.encodedPath.startsWith("/tpass/login")
        if (stillHasForm || stillOnLogin) {
            val signal = failurePattern.find(resultBody)?.value
            val suffix = if (signal != null) ": $signal" else ""
            throw JwxtAuthenticationException("CAS password login did not complete$suffix")
        }
        if (!finalHost.endsWith("neu.edu.cn")) {
            throw JwxtAuthenticationException("CAS redirected outside the expected NEU domains")
        }
        return JwxtCasLoginResult(
            ok = true,
            account = username,
            finalUrl = resultUrl.newBuilder().query(null).fragment(null).build().toString()
        )
    }
}
