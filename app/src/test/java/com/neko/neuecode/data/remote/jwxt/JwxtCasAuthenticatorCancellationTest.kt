package com.neko.neuecode.data.remote.jwxt

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class JwxtCasAuthenticatorCancellationTest {
    private val service = "https://ecode.neu.edu.cn/ecode/api/sso/login"

    @Test
    fun cancellationWhileWaitingForLoginHeadersStopsRequestChain() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(loginPage()).setHeadersDelay(2, TimeUnit.SECONDS))
            val call = AtomicReference<Call>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callStart(started: Call) { call.set(started) }
            }).build()
            val job = launch(Dispatchers.IO) {
                JwxtCasAuthenticator(client, server.url("/tpass/login").toString())
                    .loginCancellable("test-user", "test-password", service)
                fail("cancelled login completed")
            }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(1_500) { job.cancelAndJoin() }
            assertTrue(call.get().isCanceled())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun cancellationDuringLoginPageBodyDoesNotFetchScriptOrPost() =
        cancelDuringBody(step = 0)

    @Test
    fun cancellationDuringJavascriptBodyDoesNotPostPassword() =
        cancelDuringBody(step = 1)

    @Test
    fun cancellationDuringPasswordResponseBodyClosesCall() =
        cancelDuringBody(step = 2)

    private fun cancelDuringBody(step: Int) = runBlocking {
        MockWebServer().use { server ->
            val bodies = listOf(loginPage(), publicKeyScript(), loginPage())
            bodies.forEachIndexed { index, body ->
                server.enqueue(MockResponse().setBody(body).apply {
                    if (index == step) setBodyDelay(2, TimeUnit.SECONDS)
                })
            }
            val probe = CasBodyProbe(
                path = if (step == 1) "/login_neu.js" else "/tpass/login",
                method = if (step == 2) "POST" else "GET",
            )
            val client = OkHttpClient.Builder().addNetworkInterceptor(probe.interceptor).build()
            val job = launch(Dispatchers.IO) {
                JwxtCasAuthenticator(client, server.url("/tpass/login").toString())
                    .loginCancellable("test-user", "test-password", service)
                fail("cancelled login completed")
            }
            try {
                assertTrue("must cancel a real blocked body read", probe.readEntered.await(2, TimeUnit.SECONDS))
                withTimeout(1_500) { job.cancelAndJoin() }
                assertTrue(probe.call.get().isCanceled())
                assertEquals("body must be closed before returning", 0L, probe.closed.count)
                assertEquals(step + 1, server.requestCount)
                repeat(step + 1) { index ->
                    val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                    assertEquals(if (index == 2) "POST" else "GET", request.method)
                }
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun cancellationAfterPageFinishesDoesNotStartJavascript() =
        cancelAfterResponse(path = "/tpass/login", expectedRequests = 1)

    @Test
    fun cancellationAfterJavascriptFinishesDoesNotPostPassword() =
        cancelAfterResponse(path = "/login_neu.js", expectedRequests = 2)

    private fun cancelAfterResponse(path: String, expectedRequests: Int) = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(loginPage()))
            server.enqueue(MockResponse().setBody(publicKeyScript()))
            server.enqueue(MockResponse().setBody(loginPage()))
            val owner = AtomicReference<Job>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callEnd(call: Call) {
                    if (call.request().url.encodedPath == path) owner.get().cancel()
                }
            }).build()
            val job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                JwxtCasAuthenticator(client, server.url("/tpass/login").toString())
                    .loginCancellable("test-user", "test-password", service)
                fail("late success resumed a cancelled login")
            }
            owner.set(job)
            job.start()
            withTimeout(2_000) { job.join() }
            assertTrue(job.isCancelled)
            assertEquals(expectedRequests, server.requestCount)
        }
    }

    @Test
    fun synchronousAndCancellableChallengePreserveActualHtmlAndResponseUrl() = runBlocking {
        for (cancellable in listOf(false, true)) {
            for (afterPost in listOf(false, true)) {
                MockWebServer().use { server ->
                    if (afterPost) {
                        server.enqueue(MockResponse().setBody(loginPage()))
                        server.enqueue(MockResponse().setBody(publicKeyScript()))
                    }
                    // Redirect ensures this is the actual response URL, not the form action.
                    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/challenge?state=test"))
                    val html = """
                        <html><head><script src="/login_second.js"></script></head><body>
                          <div>当前设备需进行身份验证</div>
                          <form id="second_auth_form" action="/submit">
                            <input name="lt" value="LT-LIVE-TEST" />
                            <input name="execution" value="e2s1" />
                            <input name="authCode" id="scendAuthCode" />
                          </form>
                        </body></html>
                    """.trimIndent()
                    server.enqueue(MockResponse().setBody(html))
                    val auth = JwxtCasAuthenticator(OkHttpClient(), server.url("/tpass/login").toString())
                    try {
                        if (cancellable) auth.loginCancellable("test-user", "test-password", service)
                        else auth.login("test-user", "test-password", service)
                        fail("expected live MFA")
                    } catch (e: JwxtHumanVerificationRequired) {
                        assertEquals(html, e.pageHtml)
                        assertEquals(server.url("/challenge?state=test").toString(), e.pageUrl)
                        assertEquals("LT-LIVE-TEST", e.challenge?.fields?.get("lt"))
                        assertFalse("HTML must not leak in exceptions", e.toString().contains("LT-LIVE-TEST"))
                    }
                    assertEquals(if (afterPost) 4 else 2, server.requestCount)
                }
            }
        }
    }

    private fun loginPage(): String = """
        <html><body>
          <form id="loginForm" method="post" action="/tpass/login">
            <input name="lt" value="LT-TEST" />
            <input name="execution" value="e1s1" />
            <input name="_eventId" value="submit" />
            <input name="un" id="un" />
            <input name="pd" id="pd" type="password" />
          </form>
          <script type="text/html"><div>当前设备需进行身份验证</div></script>
          <script src="/login_neu.js"></script>
        </body></html>
    """.trimIndent()

    private fun publicKeyScript(): String {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return "const publicKeyStr = '${Base64.getEncoder().encodeToString(pair.public.encoded)}';"
    }
}

/** Observes a real socket body; never substitutes a response or manufactures an I/O failure. */
internal class CasBodyProbe(
    private val path: String,
    private val method: String = "GET",
    private val beforeClose: () -> Unit = {},
) {
    val readEntered = CountDownLatch(1)
    val closed = CountDownLatch(1)
    val call = AtomicReference<Call>()
    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val original = response.body
        if (chain.request().url.encodedPath != path || chain.request().method != method || original == null) {
            response
        } else {
            call.set(chain.call())
            val source = object : ForwardingSource(original.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    readEntered.countDown()
                    return super.read(sink, byteCount)
                }
                override fun close() {
                    try {
                        beforeClose()
                        super.close()
                    } finally {
                        closed.countDown()
                    }
                }
            }.buffer()
            response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = original.contentType()
                override fun contentLength() = original.contentLength()
                override fun source() = source
            }).build()
        }
    }
}
