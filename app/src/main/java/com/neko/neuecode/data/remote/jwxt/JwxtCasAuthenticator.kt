package com.neko.neuecode.data.remote.jwxt

import com.neko.neuecode.data.remote.NeuCampusHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

open class JwxtAuthenticationException(message: String) : IOException(message)
class JwxtHumanVerificationRequired(
    message: String,
    val challenge: CasSecondAuthChallenge? = null,
    // In-memory only: preserve live hidden fields/scripts for the human-verification WebView.
    val pageHtml: String? = null,
    val pageUrl: String? = null,
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
        private val failurePattern = Regex("账号或密码错误|登录失败|认证失败|剩余.{0,20}次")
    }

    private val http: OkHttpClient = sharedHttp.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun login(username: String, password: String, service: String): JwxtCasLoginResult =
        loginFlow(username, password, service) { request ->
            http.newCall(request).execute().readCasBody()
        }

    /** Cancels only this login's calls, including body reads, before releasing the caller. */
    suspend fun loginCancellable(username: String, password: String, service: String): JwxtCasLoginResult =
        withContext(Dispatchers.IO) {
            loginFlow(username, password, service) { request ->
                // Also check between page parsing, key extraction/encryption and the password POST.
                currentCoroutineContext().ensureActive()
                val response = http.newCall(request).awaitBody()
                currentCoroutineContext().ensureActive()
                response
            }.also { currentCoroutineContext().ensureActive() }
        }

    // Inline keeps the exact same parsing/submission flow for synchronous and suspending callers.
    private inline fun loginFlow(
        username: String,
        password: String,
        service: String,
        fetch: (Request) -> CasHttpResponse,
    ): JwxtCasLoginResult {
        val serviceUrl = service.toHttpUrl()
        if (serviceUrl.scheme != "https" || !serviceUrl.host.endsWith("neu.edu.cn")) {
            throw JwxtAuthenticationException("CAS service must be an HTTPS neu.edu.cn URL")
        }
        val loginUrl = loginEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("service", service)
            .build()
        val page = fetch(
            Request.Builder()
                .url(loginUrl)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .get()
                .build()
        )
        val pageUrl = page.url
        val pageCode = page.code
        val pageBody = page.body
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
                pageHtml = pageBody,
                pageUrl = pageUrl.toString(),
            )
        }
        val scriptUrl = JwxtCasCrypto.extractLoginScriptUrl(pageBody, pageUrl.toString())
            ?: throw JwxtAuthenticationException("CAS RSA public key is unavailable")
        val script = fetch(
            Request.Builder()
                .url(scriptUrl)
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .get()
                .build()
        )
        if (!script.isSuccessful) throw JwxtAuthenticationException("CAS RSA public key is unavailable")
        val publicKey = JwxtCasCrypto.extractPublicKeyFromJs(script.body)
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
        val result = fetch(
            Request.Builder()
                .url(submission.action)
                .header("Referer", pageUrl.toString())
                .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT)
                .post(form)
                .build()
        )
        val resultUrl = result.url
        val resultCode = result.code
        val resultBody = result.body
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
        if (JwxtCasCrypto.looksLikeSmsChallenge(resultBody)) {
            throw JwxtHumanVerificationRequired(
                "CAS requires live SMS/CAPTCHA/device verification",
                CasSecondAuthParser.parse(resultBody, resultUrl.toString()),
                pageHtml = resultBody,
                pageUrl = resultUrl.toString(),
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
