package com.neko.neuecode.data.remote.jwxt

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

/** Sanitized login-page fixture captured from the new NEU frontend. */
class CasLoginPageRegressionTest {
    private fun page(): String = checkNotNull(javaClass.getResource("/cas/login-neu-new.html"))
        .readText()

    @Test
    fun newPasswordPageDoesNotActivateEmbeddedSmsOrDeviceTemplates() {
        val html = page()
        assertFalse(CasSecondAuthParser.isChallengeHtml(html))
        assertFalse(JwxtCasCrypto.looksLikeSmsChallenge(html))
        val challenge = CasSecondAuthParser.parse(html, "https://pass.neu.edu.cn/tpass/login")
        assertFalse(challenge.isPresent)
        assertNull(challenge.maskedPhone)
        assertNull(challenge.captchaSrc)
        assertTrue(challenge.fields.isEmpty())
        assertEquals(
            "https://pass.neu.edu.cn/tpass/comm/neu/js/login_neu_new.js",
            JwxtCasCrypto.extractLoginScriptUrl(html, "https://pass.neu.edu.cn/tpass/login"),
        )
    }

    @Test
    fun actualChallengeUsesActiveFormNotEarlierTemplatePlaceholders() {
        val html = """
            <script type="text/html" id="second_auth_template">
              <form id="second_auth_form" action="/wrong">
                <input name="lt" value="TEMPLATE" />
                <img id="codeImage" src="/wrong-image" />
                <span>绑定手机尾号：*******0000</span>
              </form>
            </script>
            <div>当前设备需进行身份验证</div><span>绑定手机尾号：*******1234</span>
            <form id='second_auth_form' action='/tpass/login?service=test'>
              <input type='hidden' name='lt' value='LT-ACTIVE-TEST' />
              <input type='hidden' name='execution' value='e2s1' />
              <input type='hidden' name='_eventId' value='submit' />
              <img id='codeImage' src='/tpass/code?test' />
              <input id='scendAuthCode' name='authCode' />
            </form>
            <script src='/tpass/comm/neu/js/login_second.js'></script>
        """.trimIndent()
        val challenge = CasSecondAuthParser.parse(html, "https://pass.neu.edu.cn/tpass/login")
        assertTrue(challenge.isPresent)
        assertEquals("*******1234", challenge.maskedPhone)
        assertEquals("LT-ACTIVE-TEST", challenge.fields["lt"])
        assertEquals("/tpass/code?test", challenge.captchaSrc)
        assertEquals("https://pass.neu.edu.cn/tpass/login?service=test", challenge.formAction)
        assertTrue(challenge.needsGraphicCaptcha)
        assertTrue(challenge.needsSmsCode)
    }

    @Test
    fun singleQuotedPasswordFormAndInertTemplatesAreNotAChallenge() {
        val html = """
            <form id = 'loginForm'><input id = 'un'/><input id = 'pd'/></form>
            <script>const text = '输入验证码';</script>
            <template><div>当前设备需进行身份验证</div></template>
            <!-- <form id='second_auth_form'>二次认证</form> -->
            <div id='template_phone' style='display:none'><a>获取验证码</a></div>
        """.trimIndent()
        assertFalse(JwxtCasCrypto.looksLikeSmsChallenge(html))
    }

    @Test
    fun activeGraphicCaptchaStillStopsPasswordAutomation() {
        val html = """<form id='loginForm'><input id='un'/><input id='pd'/>
            <input name='code' placeholder='请输入图形验证码'/><img id='codeImage' src='/tpass/code'/></form>"""
        assertTrue(JwxtCasCrypto.looksLikeSmsChallenge(html))
    }

    @Test
    fun newFrontendSubmitsRsaOnceAndPreservesDynamicHiddenFields() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(page()))
            server.enqueue(MockResponse().setBody("const publicKeyStr = '${Base64.getEncoder().encodeToString(pair.public.encoded)}';"))
            server.enqueue(MockResponse().setBody(page()))
            val authenticator = JwxtCasAuthenticator(OkHttpClient(), server.url("/tpass/login").toString())
            try {
                authenticator.login("test-user", "test-password", JwxtScheduleClient.HOME_SERVICE)
                fail("a returned login form must not be accepted")
            } catch (e: JwxtAuthenticationException) {
                assertFalse("inactive templates must not be reported as live MFA", e is JwxtHumanVerificationRequired)
            }
            assertEquals(3, server.requestCount)
            assertEquals("GET", server.takeRequest(1, TimeUnit.SECONDS)!!.method)
            assertEquals("/tpass/comm/neu/js/login_neu_new.js", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
            val post = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST", post.method)
            val fields = post.body.readUtf8().split('&').associate {
                val parts = it.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
            assertFalse(fields.containsKey("un"))
            assertFalse(fields.containsKey("pd"))
            assertEquals("[REDACTED]", fields["lt"])
            assertEquals("[REDACTED]", fields["execution"])
            assertEquals("submit", fields["_eventId"])
            assertEquals("", fields["t_un"])
            assertEquals("", fields["t_pd"])
            assertEquals("", fields["t_c"])
            val decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            decrypt.init(Cipher.DECRYPT_MODE, pair.private)
            assertEquals("test-usertest-password", String(decrypt.doFinal(Base64.getDecoder().decode(fields["rsa"])), Charsets.UTF_8))
        }
    }

    @Test
    fun newFrontendPostCanStillReturnRealSecondAuth() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(page()))
            server.enqueue(MockResponse().setBody("const publicKeyStr = '${Base64.getEncoder().encodeToString(pair.public.encoded)}';"))
            server.enqueue(MockResponse().setBody("""
                <div>当前设备需进行身份验证</div>
                <form id="second_auth_form" action="/tpass/login">
                  <input name="lt" value="LT-SECOND-TEST"/><input name="authCode" id="scendAuthCode"/>
                </form>
            """.trimIndent()))
            try {
                JwxtCasAuthenticator(OkHttpClient(), server.url("/tpass/login").toString())
                    .login("test-user", "test-password", JwxtScheduleClient.HOME_SERVICE)
                fail("expected active second authentication")
            } catch (e: JwxtHumanVerificationRequired) {
                val challenge = checkNotNull(e.challenge)
                assertTrue(challenge.isPresent)
                assertEquals("LT-SECOND-TEST", challenge.fields["lt"])
            }
            assertEquals(3, server.requestCount)
        }
    }
}
