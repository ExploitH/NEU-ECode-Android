package com.neko.neuecode.data.remote.jwxt

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CasSecondAuthClientTest {

    @Test
    fun fetchCaptchaImage_sendsCasSessionCookie() {
        MockWebServer().use { server ->
            val sessionCookie = Cookie.parse(
                server.url("/"),
                "JSESSIONID=cas-session; Path=/",
            )!!
            val http = OkHttpClient.Builder()
                .cookieJar(staticJar(sessionCookie))
                .build()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path.orEmpty().startsWith("/tpass/code")) {
                        val cookie = request.getHeader("Cookie").orEmpty()
                        val body = if (cookie.contains("JSESSIONID=cas-session")) {
                            "SESSION-CAPTCHA"
                        } else {
                            "ANON-CAPTCHA"
                        }
                        return MockResponse()
                            .setHeader("Content-Type", "image/png")
                            .setBody(body)
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
            val client = CasSecondAuthClient(http)

            val bytes = client.fetchCaptchaImage(server.url("/tpass/login").toString())

            assertArrayEquals("SESSION-CAPTCHA".toByteArray(), bytes)
            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertTrue(recorded.path.orEmpty().startsWith("/tpass/code"))
            assertTrue(recorded.getHeader("Cookie").orEmpty().contains("JSESSIONID=cas-session"))
        }
    }

    @Test
    fun fetchCaptchaImage_withoutCookie_isAnonymousImage() {
        MockWebServer().use { server ->
            val http = OkHttpClient()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path.orEmpty().startsWith("/tpass/code")) {
                        val cookie = request.getHeader("Cookie").orEmpty()
                        val body = if (cookie.contains("JSESSIONID=cas-session")) {
                            "SESSION-CAPTCHA"
                        } else {
                            "ANON-CAPTCHA"
                        }
                        return MockResponse()
                            .setHeader("Content-Type", "image/png")
                            .setBody(body)
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
            val client = CasSecondAuthClient(http)

            val bytes = client.fetchCaptchaImage(server.url("/tpass/login").toString())

            assertArrayEquals("ANON-CAPTCHA".toByteArray(), bytes)
            assertFalse(server.takeRequest().getHeader("Cookie").orEmpty().contains("JSESSIONID=cas-session"))
        }
    }

    @Test
    fun captchaImageUrl_staysOnChallengeHost() {
        val client = CasSecondAuthClient(OkHttpClient())
        val url = client.captchaImageUrl(
            "https://pass.neu.edu.cn/tpass/login?service=https%3A%2F%2Fecode.neu.edu.cn%2Fecode%2Fapi%2Fsso%2Flogin",
        )
        assertTrue(url.startsWith("https://pass.neu.edu.cn/tpass/code?"))
    }

    @Test
    fun sendSmsCode_postsOfficialFieldsWithSessionCookie() {
        MockWebServer().use { server ->
            val sessionCookie = Cookie.parse(
                server.url("/"),
                "JSESSIONID=cas-session; Path=/",
            )!!
            val http = OkHttpClient.Builder()
                .cookieJar(staticJar(sessionCookie))
                .build()
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"success":false,"message":"图形验证码错误"}"""),
            )
            val client = CasSecondAuthClient(http)

            val result = client.sendSmsCode(
                pageUrl = server.url("/tpass/login?service=abc").toString(),
                graphicCaptcha = "1189",
            )

            assertFalse(result.ok)
            assertEquals("图形验证码错误", result.message)
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/tpass/secondAuthCode", recorded.path)
            assertEquals("code=1189&method=mobile", recorded.body.readUtf8())
            assertTrue(recorded.getHeader("Cookie").orEmpty().contains("JSESSIONID=cas-session"))
        }
    }

    private fun staticJar(cookie: Cookie): CookieJar {
        return object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return if (cookie.matches(url)) listOf(cookie) else emptyList()
            }
        }
    }
}
