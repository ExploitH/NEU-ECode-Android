package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.NeuCampusHttp
import com.neko.neuecode.data.remote.ecode.ECodePayCodeApi
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.data.remote.jwxt.JwxtCasLoginResult
import com.neko.neuecode.data.remote.jwxt.JwxtHumanVerificationRequired
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    fun fetchPayCode_withoutCredentials_returnsUnauthenticated() = runBlocking {
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns null
        val repository = ECodePayCodeRepository(
            api = ECodePayCodeApi(OkHttpClient()),
            authenticator = mockk(relaxed = true),
            credentialStore = store,
            http = OkHttpClient(),
            cookieJar = mockk(relaxed = true),
        )

        val result = repository.fetchPayCode(nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.Unauthenticated, failure.reason)
    }

    @Test
    fun fetchPayCode_logsIntoEcodeSsoThenGetsQrCode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>home</html>"))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"data":[{"type":null,"attributes":{"qrCode":"NEU-PAY-FIXTURE-001","createTime":"1710000000000","qrInvalidTime":"1710000090000"}}]}""",
                    ),
            )
            val authenticator = mockk<JwxtCasAuthenticator>()
            every { authenticator.login(any(), any(), any()) } returns JwxtCasLoginResult(
                ok = true,
                account = "20240001",
                finalUrl = "https://ecode.neu.edu.cn/ecode/api",
            )
            val store = mockk<SecureCredentialStore>()
            every { store.load() } returns SecureCredentialStore.Credentials("20240001", "secret")
            val client = OkHttpClient()
            val repository = ECodePayCodeRepository(
                api = ECodePayCodeApi(client, server.url("/").toString().trimEnd('/')),
                authenticator = authenticator,
                credentialStore = store,
                http = warmupClient(server, client),
                cookieJar = mockk(relaxed = true),
            )

            val result = repository.fetchPayCode(nowEpochMs)

            val success = result as PayCodeParseResult.Success
            assertEquals("NEU-PAY-FIXTURE-001", success.code.payload)
            verify(exactly = 1) {
                authenticator.login("20240001", "secret", NeuCampusHttp.ECODE_SSO)
            }
        }
    }

    @Test
    fun fetchPayCode_http401_retriesCasThenGetsQrCode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
            server.enqueue(MockResponse().setBody("ok"))
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
        }
    }

    @Test
    fun fetchPayCode_htmlLoginPage_returnsNeedRelogin() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
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

    @Test
    fun fetchPayCode_smsChallenge_returnsNeedSmsWithoutRetryingLogin() = runBlocking {
        val authenticator = mockk<JwxtCasAuthenticator>()
        every { authenticator.login(any(), any(), any()) } throws
            JwxtHumanVerificationRequired("CAS requires live SMS/CAPTCHA/device verification")
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns SecureCredentialStore.Credentials("20240001", "secret")
        val repository = ECodePayCodeRepository(
            api = ECodePayCodeApi(OkHttpClient()),
            authenticator = authenticator,
            credentialStore = store,
            http = OkHttpClient(),
            cookieJar = mockk(relaxed = true),
        )

        val first = repository.fetchPayCode(nowEpochMs) as PayCodeParseResult.Failure
        val second = repository.fetchPayCode(nowEpochMs) as PayCodeParseResult.Failure

        assertEquals(PayCodeFailure.NeedSms, first.reason)
        assertEquals(PayCodeFailure.NeedSms, second.reason)
        assertTrue(first.message.orEmpty().contains("不要反复点刷新"))
        assertTrue(first.message.orEmpty().contains("图形验证码"))
        verify(exactly = 1) { authenticator.login(any(), any(), any()) }
    }

    @Test
    fun fetchPayCode_http502ThenSuccess_retries() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
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
        }
    }

    private fun repository(server: MockWebServer): ECodePayCodeRepository {
        val authenticator = mockk<JwxtCasAuthenticator>()
        every { authenticator.login(any(), any(), any()) } returns JwxtCasLoginResult(
            ok = true,
            account = "20240001",
            finalUrl = "https://ecode.neu.edu.cn/ecode/api",
        )
        val store = mockk<SecureCredentialStore>()
        every { store.load() } returns SecureCredentialStore.Credentials("20240001", "secret")
        val client = OkHttpClient()
        return ECodePayCodeRepository(
            api = ECodePayCodeApi(client, server.url("/").toString().trimEnd('/')),
            authenticator = authenticator,
            credentialStore = store,
            http = warmupClient(server, client),
            cookieJar = mockk(relaxed = true),
        )
    }

    private fun warmupClient(server: MockWebServer, client: OkHttpClient): OkHttpClient {
        return client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host == "ecode.neu.edu.cn") {
                    chain.proceed(request.newBuilder().url(server.url("/ecode/")).build())
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }
}
