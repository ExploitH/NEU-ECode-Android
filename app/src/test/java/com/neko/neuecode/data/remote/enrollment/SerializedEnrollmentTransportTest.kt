package com.neko.neuecode.data.remote.enrollment

import com.google.gson.JsonParser
import com.neko.neuecode.data.local.cookie.CookieSerializer
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SerializedEnrollmentTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var transport: SerializedEnrollmentTransport

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val serializer = mockk<CookieSerializer>(relaxed = true)
        coEvery { serializer.loadCookies() } returns emptyList()
        cookieJar = PersistentCookieJar(serializer)

        val seedUrl = server.url("/xsxk/elective/neu/xskb")
        cookieJar.saveFromResponse(
            seedUrl,
            listOf(
                Cookie.Builder()
                    .name("JSESSIONID")
                    .value("seed")
                    .hostOnlyDomain(seedUrl.host)
                    .path("/xsxk")
                    .httpOnly()
                    .build()
            )
        )
        transport = SerializedEnrollmentTransport(
            client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(false)
                .build(),
            baseUrl = server.url("/xsxk").toString(),
            minRequestIntervalMs = 0L
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `rotating JSESSIONID is injected before each serialized request`() = runTest {
        server.enqueue(jsonResponse("A"))
        server.enqueue(jsonResponse("B"))
        server.enqueue(jsonResponse("C"))
        val session = EnrollmentSessionHeaders("test-authorization", "test-batch")

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            transport.postReadOnly(
                EnrollmentReadEndpoint.SCHEDULE,
                session,
                EnrollmentRequestBody.Form(mapOf("campus" to "01"))
            )
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            transport.postReadOnly(
                EnrollmentReadEndpoint.CATALOG,
                session,
                EnrollmentRequestBody.Json(mapOf("pageNumber" to 1))
            )
        }
        first.await()
        second.await()
        transport.postReadOnly(EnrollmentReadEndpoint.ALL_SELECTED, session)

        val requests = List(3) {
            server.takeRequest(3, TimeUnit.SECONDS) ?: error("missing request ${it + 1}")
        }
        assertEquals("JSESSIONID=seed", requests[0].getHeader("Cookie"))
        assertEquals("JSESSIONID=A", requests[1].getHeader("Cookie"))
        assertEquals("JSESSIONID=B", requests[2].getHeader("Cookie"))
        assertEquals("test-authorization", requests[2].getHeader("Authorization"))
        assertEquals("test-batch", requests[2].getHeader("batchId"))
        assertEquals("application/x-www-form-urlencoded", requests[0].getHeader("Content-Type"))
        assertEquals("campus=01", requests[0].body.readUtf8())
        assertEquals("application/json; charset=utf-8", requests[1].getHeader("Content-Type"))
        assertEquals(
            1,
            JsonParser.parseString(requests[1].body.readUtf8()).asJsonObject["pageNumber"].asInt
        )
        assertEquals("{}", requests[2].body.readUtf8())

        val finalCookies = cookieJar.loadForRequest(server.url("/xsxk/elective/select"))
        assertEquals("C", finalCookies.single { it.name == "JSESSIONID" }.value)
    }

    @Test
    fun `transport exposes read endpoints only`() {
        val paths = EnrollmentReadEndpoint.entries.map { it.path }

        assertEquals(5, paths.size)
        assertFalse(paths.any { it.contains("/add") })
        assertFalse(paths.any { it.contains("/del") })
        assertFalse(paths.any { it.contains("weightAdd") })
    }

    @Test
    fun `http authentication failure is classified as expired session`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"code\":401}")
        )

        val error = runCatching {
            transport.postReadOnly(
                EnrollmentReadEndpoint.CATALOG,
                EnrollmentSessionHeaders("test-authorization", "test-batch")
            )
        }.exceptionOrNull()

        assertTrue(error is EnrollmentSessionExpiredException)
    }

    private fun jsonResponse(nextSessionId: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json;charset=UTF-8")
        .addHeader("Set-Cookie", "JSESSIONID=$nextSessionId; Path=/xsxk; HttpOnly")
        .setBody("{\"code\":200,\"msg\":\"ok\",\"data\":{}}")
}
