package com.neko.neuecode.data.remote.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.util.Base64
import javax.crypto.Cipher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.fail

class JwxtCasAuthenticatorTest {

    @Test
    fun encryptsUsernamePlusPasswordWithPkcs1() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicDer = pair.public.encoded
        val encrypted = JwxtCasCrypto.encryptCredentials(
            publicKeyB64 = Base64.getEncoder().encodeToString(publicDer),
            username = "20240001",
            password = "example-password"
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, pair.private as RSAPrivateKey)
        val encryptedBytes = Base64.getDecoder().decode(encrypted)
        val plain = String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        assertEquals("20240001example-password", plain)
    }

    @Test
    fun buildLoginSubmission_excludesPlaintextUsernameAndPassword() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicB64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        val page = """
            <form id="loginForm" method="post" action="/tpass/login?service=abc">
              <input name="un" id="un" value="">
              <input name="pd" id="pd" type="password" value="">
              <input name="rsa" id="rsa" type="hidden" value="">
              <input name="ul" id="ul" type="hidden" value="">
              <input name="pl" id="pl" type="hidden" value="">
              <input name="lt" id="lt" type="hidden" value="LT-123">
              <input name="execution" type="hidden" value="e1s1">
              <input name="_eventId" type="hidden" value="submit">
            </form>
        """.trimIndent()

        val submission = JwxtCasCrypto.buildLoginSubmission(
            pageHtml = page,
            pageUrl = "https://pass.neu.edu.cn/tpass/login?service=abc",
            publicKeyB64 = publicB64,
            username = "20240001",
            password = "secret"
        )

        assertEquals("https://pass.neu.edu.cn/tpass/login?service=abc", submission.action)
        assertFalse(submission.fields.containsKey("un"))
        assertFalse(submission.fields.containsKey("pd"))
        assertEquals("LT-123", submission.fields["lt"])
        assertEquals("e1s1", submission.fields["execution"])
        assertEquals("submit", submission.fields["_eventId"])
        assertEquals("8", submission.fields["ul"])
        assertEquals("6", submission.fields["pl"])
        assertTrue(submission.fields["rsa"].orEmpty().isNotBlank())
    }

    @Test
    fun login_smsChallengePage_throwsHumanVerificationInsteadOfMissingKey() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    <html><body>
                      <div>二次认证</div>
                      <p>登录码已发送，请输入验证码</p>
                      <input id="mcode" />
                    </body></html>
                    """.trimIndent(),
                ),
            )
            val authenticator = JwxtCasAuthenticator(
                sharedHttp = OkHttpClient(),
                loginEndpoint = server.url("/tpass/login").toString(),
            )
            try {
                authenticator.login("20240001", "secret", "https://ecode.neu.edu.cn/ecode/api/sso/login")
                fail("expected SMS challenge")
            } catch (e: JwxtHumanVerificationRequired) {
                assertTrue(e.message.orEmpty().contains("SMS"))
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun login_passwordPageWithoutScript_stillReportsMissingKey() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """<html><body><form id="loginForm"><input id="un"/><input id="pd"/></form></body></html>""",
                ),
            )
            val authenticator = JwxtCasAuthenticator(
                sharedHttp = OkHttpClient(),
                loginEndpoint = server.url("/tpass/login").toString(),
            )
            try {
                authenticator.login("20240001", "secret", "https://ecode.neu.edu.cn/ecode/api/sso/login")
                fail("expected missing RSA key")
            } catch (e: JwxtAuthenticationException) {
                assertFalse(e is JwxtHumanVerificationRequired)
                assertTrue(e.message.orEmpty().contains("RSA public key is unavailable"))
            }
        }
    }
}
