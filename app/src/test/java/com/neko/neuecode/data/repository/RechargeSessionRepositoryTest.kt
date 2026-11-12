package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.cookie.SerializableCookie
import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtCasLoginResult
import com.neko.neuecode.data.remote.jwxt.JwxtHumanVerificationRequired
import com.neko.neuecode.data.remote.jwxt.CasSecondAuthChallenge
import com.neko.neuecode.data.remote.jwxt.CasSecondAuthParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RechargeSessionRepositoryTest {
    private val PORTAL_HTML = """<html>缴费服务大厅<a href="/openApplication">应用</a><a href="/orders.html">订单</a><a href="/openPayUserInvoiceList">发票抬头</a></html>"""
    @Test
    fun preparationNeverImportsAttributeLessWebViewCookieHeaders() = runBlocking {
        val jar = emptyJar()
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns null
        RechargeSessionRepository(OkHttpClient(), mockk(), store, jar).prepare()
        coVerify(exactly = 0) { jar.snapshotFromWebView(any()) }
    }

    @Test
    fun maintenancePageCannotBeTreatedAsAuthenticatedPortal() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>缴费系统正在维护</html>"))
            val auth = mockk<JwxtCasAuthenticator>()
            coEvery { auth.loginCancellable(any(), any(), any()) } returns JwxtCasLoginResult(true, "fixture", RechargeSessionRepository.PORTAL_URL)
            val result = RechargeSessionRepository(mappedHttp(server), auth, savedStore(), emptyJar()).prepare()
            assertTrue(result is RechargeSession.ManualLogin)
        }
    }

    @Test
    fun loginShell_recognizesCurrentAndLegacyPaymentHallPaths() {
        assertTrue(RechargeSessionRepository.isLoginShell("https://pay.neu.edu.cn/inerlogin.html"))
        assertTrue(RechargeSessionRepository.isLoginShell("https://pay.neu.edu.cn/tologin.html"))
        assertTrue(RechargeSessionRepository.isLoginShell("https://pay.neu.edu.cn/inerlogin.html;jsessionid=stale"))
        assertFalse(RechargeSessionRepository.isLoginShell(RechargeSessionRepository.PORTAL_URL))
        assertFalse(RechargeSessionRepository.isLoginShell("https://pay.neu.edu.cn.evil.test/inerlogin.html"))
        assertFalse(RechargeSessionRepository.isLoginShell("https://pay.neu.edu.cn/inerlogin.html.evil"))
    }

    @Test
    fun noStoredCredentialsLeavesManualLoginWithoutSubmittingPassword() = runBlocking {
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns null
        val auth = mockk<JwxtCasAuthenticator>()
        val result = RechargeSessionRepository(OkHttpClient(), auth, store, emptyJar()).prepare()
        assertTrue(result is RechargeSession.ManualLogin)
        coVerify(exactly = 0) { auth.loginCancellable(any(), any(), any()) }
    }

    @Test
    fun activePaySessionSkipsPasswordLogin() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(PORTAL_HTML))
            val jar = emptyJar()
            every { jar.getCookiesForUrl(any()) } returns listOf(
                SerializableCookie("JSESSIONID", "fixture-session", "pay.neu.edu.cn"),
            )
            val auth = mockk<JwxtCasAuthenticator>()
            val result = RechargeSessionRepository(mappedHttp(server), auth, mockk(), jar).prepare()
            assertEquals(RechargeSession.Ready(RechargeSessionRepository.PORTAL_URL), result)
            coVerify(exactly = 0) { auth.loginCancellable(any(), any(), any()) }
        }
    }

    @Test
    fun casSuccessWithoutUsablePayPageDoesNotClaimReady() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<form id='loginForm'>缴费服务大厅</form>"))
            val auth = mockk<JwxtCasAuthenticator>()
            coEvery { auth.loginCancellable(any(), any(), any()) } returns JwxtCasLoginResult(true, "fixture", RechargeSessionRepository.PORTAL_URL)
            val result = RechargeSessionRepository(mappedHttp(server), auth, savedStore(), emptyJar()).prepare()
            assertTrue(result is RechargeSession.ManualLogin)
            coVerify(exactly = 1) { auth.loginCancellable(any(), any(), RechargeSessionRepository.CAS_SERVICE) }
        }
    }

    @Test
    fun securityChallengeContinuesExistingCasFlowWithoutRetryingPassword() = runBlocking {
        val continuation = "https://pass.neu.edu.cn/tpass/login".toHttpUrl().newBuilder()
            .addQueryParameter("service", RechargeSessionRepository.CAS_SERVICE).build().toString()
        val html = """<html><body>当前设备需进行身份验证
            <form id="second_auth_form" action="$continuation" method="post">
              <input type="hidden" name="execution" value="e2s1"/>
              <input type="hidden" name="lt" value="LT-fixture"/>
              <input type="hidden" name="_eventId" value="submit"/>
              <input name="authCode" id="scendAuthCode"/>
            </form></body></html>"""
        val challenge = CasSecondAuthParser.parse(html, continuation)
        assertFalse(continuation.contains("execution"))
        val auth = mockk<JwxtCasAuthenticator>()
        coEvery { auth.loginCancellable(any(), any(), any()) } throws JwxtHumanVerificationRequired(
            "verification", challenge, pageHtml = html, pageUrl = continuation,
        )
        val result = RechargeSessionRepository(OkHttpClient(), auth, savedStore(), emptyJar()).prepare()
        assertEquals(continuation, (result as RechargeSession.Verification).baseUrl)
        assertEquals(html, result.html)
        val handedOff = CasSecondAuthParser.parse(result.html, result.baseUrl)
        assertEquals("e2s1", handedOff.fields["execution"])
        assertEquals("LT-fixture", handedOff.fields["lt"])
        coVerify(exactly = 1) { auth.loginCancellable(any(), any(), any()) }
    }

    @Test
    fun unexpectedChallengeOriginFallsBackToKnownPayService() = runBlocking {
        val auth = mockk<JwxtCasAuthenticator>()
        coEvery { auth.loginCancellable(any(), any(), any()) } throws JwxtHumanVerificationRequired(
            "verification", CasSecondAuthChallenge(true, formAction = "https://pass.neu.edu.cn.evil.test/tpass/login"),
        )
        val result = RechargeSessionRepository(OkHttpClient(), auth, savedStore(), emptyJar()).prepare()
        assertEquals(RechargeSessionRepository.CAS_SERVICE, (result as RechargeSession.ManualLogin).url)
    }

    @Test(expected = CancellationException::class)
    fun leavingScreenPropagatesCancellation() = runBlocking {
        val auth = mockk<JwxtCasAuthenticator>()
        coEvery { auth.loginCancellable(any(), any(), any()) } throws CancellationException("screen left")
        RechargeSessionRepository(OkHttpClient(), auth, savedStore(), emptyJar()).prepare()
        Unit
    }

    private fun savedStore() = mockk<SecureCredentialStore>().also {
        every { it.load() } returns SecureCredentialStore.Credentials("fixture", "test-password")
    }

    private fun emptyJar() = mockk<PersistentCookieJar>(relaxed = true).also {
        every { it.getCookiesForUrl(any()) } returns emptyList()
    }

    private fun mappedHttp(server: MockWebServer) = OkHttpClient.Builder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(server.url("/openPortal")).build())
            .newBuilder().request(chain.request()).build()
    }.build()

    @Test
    fun expiredPaySessionRestoresUsingSavedCredentialsAndReloadsPortal() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(PORTAL_HTML))
            val auth = mockk<JwxtCasAuthenticator>()
            coEvery { auth.loginCancellable(any(), any(), any()) } returns JwxtCasLoginResult(
                true, "fixture", "https://pay.neu.edu.cn/openPortal;jsessionid=private-session",
            )
            val store = mockk<SecureCredentialStore>()
            every { store.load() } returns SecureCredentialStore.Credentials("fixture", "test-password")
            val jar = mockk<PersistentCookieJar>(relaxed = true)
            coEvery { jar.restoreFromStorage() } returns Unit
            every { jar.getCookiesForUrl(any()) } returns emptyList()
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/openPortal")).build())
                    .newBuilder().request(chain.request()).build()
            }.build()

            val result = RechargeSessionRepository(http, auth, store, jar).prepare()

            assertEquals(RechargeSession.Ready(RechargeSessionRepository.PORTAL_URL), result)
            coVerify(exactly = 1) { auth.loginCancellable("fixture", "test-password", RechargeSessionRepository.CAS_SERVICE) }
        }
    }
}
