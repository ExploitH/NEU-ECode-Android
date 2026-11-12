package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.NeuCampusHttp
import com.neko.neuecode.data.remote.readRechargePage
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtHumanVerificationRequired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class RechargeSession {
    data class Ready(val url: String) : RechargeSession()
    data class ManualLogin(val url: String, val message: String) : RechargeSession()
    // Intentionally not a data class: HTML holds live hidden fields and must not be logged.
    class Verification(val baseUrl: String, val html: String) : RechargeSession()
    data class Error(val message: String) : RechargeSession()
}

@Singleton
class RechargeSessionRepository @Inject constructor(
    http: OkHttpClient,
    private val authenticator: JwxtCasAuthenticator,
    private val credentials: SecureCredentialStore,
    private val cookieJar: PersistentCookieJar,
) {
    companion object {
        const val PORTAL_URL = "https://pay.neu.edu.cn/openPortal"
        const val CAS_SERVICE = "https://pay.neu.edu.cn/drCasLogin?redirectUrl=openBindInfo"

        fun isLoginShell(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            return parsed.host == "pay.neu.edu.cn" &&
                parsed.encodedPath.substringBefore(';') in setOf("/tologin.html", "/inerlogin.html")
        }

        private fun isRechargeCasPage(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            return parsed.scheme == "https" && parsed.host == "pass.neu.edu.cn" &&
                parsed.port == 443 && parsed.encodedPath == "/tpass/login" &&
                parsed.queryParameter("service") == CAS_SERVICE
        }
    }

    private val http = http.newBuilder().followRedirects(true).followSslRedirects(true)
        .callTimeout(40, TimeUnit.SECONDS).build()
    private val preparationLock = Mutex()

    suspend fun prepare(): RechargeSession = preparationLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                cookieJar.restoreFromStorage()
                currentCoroutineContext().ensureActive()
                // Never reverse-import WebView's name=value header: it lacks Path/HttpOnly/expiry.
                if (cookieJar.getCookiesForUrl(PORTAL_URL).any { it.name == "JSESSIONID" } && portalIsAuthenticated()) {
                    return@withContext RechargeSession.Ready(PORTAL_URL)
                }
                val saved = credentials.load()
                    ?: return@withContext RechargeSession.ManualLogin(CAS_SERVICE, "请在下方完成统一身份认证")
                currentCoroutineContext().ensureActive()
                authenticator.loginCancellable(saved.username, saved.password, CAS_SERVICE)
                currentCoroutineContext().ensureActive()
                // CAS success alone is insufficient: verify the pay service session.
                // Never load an unvalidated finalUrl or its ;jsessionid into WebView.
                if (portalIsAuthenticated()) RechargeSession.Ready(PORTAL_URL)
                else RechargeSession.ManualLogin(CAS_SERVICE, "充值登录未完成，请在下方继续认证")
            } catch (e: CancellationException) {
                throw e
            } catch (e: JwxtHumanVerificationRequired) {
                currentCoroutineContext().ensureActive()
                val html = e.pageHtml
                val pageUrl = e.pageUrl.orEmpty()
                if (!html.isNullOrBlank() && isRechargeCasPage(pageUrl) &&
                    isRechargeCasPage(e.challenge?.formAction.orEmpty())
                ) {
                    // Render the actual response; GET formAction would discard hidden execution/lt.
                    RechargeSession.Verification(pageUrl, html)
                } else {
                    RechargeSession.ManualLogin(CAS_SERVICE, "学校要求安全验证，请在下方重新登录并完成验证")
                }
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                RechargeSession.ManualLogin(CAS_SERVICE, "自动登录暂未完成，可在下方登录或点击刷新重试")
            }
        }
    }

    private suspend fun portalIsAuthenticated(): Boolean {
        currentCoroutineContext().ensureActive()
        val page = http.newCall(Request.Builder().url(PORTAL_URL)
            .header("User-Agent", NeuCampusHttp.BROWSER_USER_AGENT).build()).readRechargePage()
        val url = page.url
        val body = page.body
        // Verified authenticated navigation emitted by the payment hall.
        val links = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(body).map { it.groupValues[1] }.toSet()
        return page.code in 200..299 && url.scheme == "https" && url.host == "pay.neu.edu.cn" &&
            url.encodedPath.substringBefore(';') == "/openPortal" &&
            !Regex("""id\s*=\s*["']loginForm["']""", RegexOption.IGNORE_CASE).containsMatchIn(body) &&
            links.containsAll(listOf("/openApplication", "/orders.html", "/openPayUserInvoiceList"))
    }
}
