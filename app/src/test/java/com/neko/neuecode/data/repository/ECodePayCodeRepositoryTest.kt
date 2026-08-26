package com.neko.neuecode.data.repository

import com.neko.neuecode.data.remote.ecode.ECodePayCodeApi
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ECodePayCodeRepositoryTest {

    private val nowEpochMs = 1_710_000_000_000L

    @Test
    fun fetchPayCode_jsonApiSuccess_returnsPayloadAndExpiry() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"data":[{"type":null,"attributes":{"qrCode":"NEU-PAY-FIXTURE-001","createTime":"1710000000000","qrInvalidTime":"1710000090000"}}]}""",
                    ),
            )
            val repository = repository(server)

            val result = repository.fetchPayCode(nowEpochMs)

            val success = result as PayCodeParseResult.Success
            assertEquals("NEU-PAY-FIXTURE-001", success.code.payload)
            assertEquals(1_710_000_090_000L, success.code.expiresAtEpochMs)
            assertEquals(90, success.code.ttlSeconds)
            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/ecode/api/qr-code", recorded.path)
        }
    }

    @Test
    fun fetchPayCode_jsonApiExpired_returnsExpired() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"data":[{"type":null,"attributes":{"qrCode":"NEU-PAY-FIXTURE-001","createTime":"1709999910000","qrInvalidTime":"1710000000000"}}]}""",
                    ),
            )
            val repository = repository(server)

            val result = repository.fetchPayCode(nowEpochMs)

            val failure = result as PayCodeParseResult.Failure
            assertEquals(PayCodeFailure.Expired, failure.reason)
        }
    }

    @Test
    fun fetchPayCode_http401_returnsUnauthenticated() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
            val repository = repository(server)

            val result = repository.fetchPayCode(nowEpochMs)

            val failure = result as PayCodeParseResult.Failure
            assertEquals(PayCodeFailure.Unauthenticated, failure.reason)
        }
    }

    @Test
    fun fetchPayCode_http403_returnsNeedRelogin() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
            val repository = repository(server)

            val result = repository.fetchPayCode(nowEpochMs)

            val failure = result as PayCodeParseResult.Failure
            assertTrue(
                failure.reason == PayCodeFailure.NeedRelogin ||
                    failure.reason == PayCodeFailure.Unauthenticated,
            )
        }
    }

    @Test
    fun fetchPayCode_htmlLoginPage_returnsNeedRelogin() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/html; charset=UTF-8")
                    .setBody("<html><body>统一身份认证</body></html>"),
            )
            val repository = repository(server)

            val result = repository.fetchPayCode(nowEpochMs)

            val failure = result as PayCodeParseResult.Failure
            assertEquals(PayCodeFailure.NeedRelogin, failure.reason)
        }
    }

    private fun repository(server: MockWebServer): ECodePayCodeRepository {
        val api = ECodePayCodeApi(
            http = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
        return ECodePayCodeRepository(api)
    }
}
