package com.neko.neuecode.data.repository

import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.data.remote.ecode.ECodePayCodeApi
import com.neko.neuecode.data.remote.jwxt.JwxtCasAuthenticator
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ECodePayCodeSessionReuseTest {

    private val nowEpochMs = 1_710_000_000_000L
    private val qrJson =
        """{"data":[{"type":null,"attributes":{"qrCode":"NEU-PAY-FIXTURE-001","createTime":"1710000000000","qrInvalidTime":"1710000090000"}}]}"""

    @Test
    fun fetchPayCode_reusesExistingEcodeSession_withoutCallingCas() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(qrJson),
            )
            val authenticator = mockk<JwxtCasAuthenticator>(relaxed = true)
            val store = mockk<SecureCredentialStore>()
            every { store.load() } returns SecureCredentialStore.Credentials("20240001", "secret")
            val client = OkHttpClient()
            val repository = ECodePayCodeRepository(
                api = ECodePayCodeApi(client, server.url("/").toString().trimEnd('/')),
                authenticator = authenticator,
                credentialStore = store,
                http = client,
                cookieJar = mockk(relaxed = true),
            ).apply { sessionProbeOverride = { true } }

            val result = repository.fetchPayCode(nowEpochMs) as PayCodeParseResult.Success

            assertEquals("NEU-PAY-FIXTURE-001", result.code.payload)
            verify(exactly = 0) { authenticator.login(any(), any(), any()) }
        }
    }
}
