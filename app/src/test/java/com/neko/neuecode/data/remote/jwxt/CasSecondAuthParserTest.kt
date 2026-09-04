package com.neko.neuecode.data.remote.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasSecondAuthParserTest {

    private val liveSecondAuthHtml = """
        <html>
          <body>
            <div>为保障您的账号安全，当前设备需进行身份验证</div>
            <span>绑定手机尾号：*******9060</span>
            <form id="second_auth_form" action="/tpass/login?service=https%3A%2F%2Fecode.neu.edu.cn%2Fecode%2Fapi%2Fsso%2Flogin" method="post">
              <input type="hidden" name="lt" value="LT-SECOND-1"/>
              <input type="hidden" name="execution" value="e2s1"/>
              <input type="hidden" name="_eventId" value="submit"/>
              <input id="imgCode" name="imgCode" type="text"/>
              <img id="codeImage" src="/tpass/code?0.12"/>
              <input id="scendAuthCode" name="authCode" type="text"/>
              <a id="getScendAuthCode">获取验证码</a>
              <input type="button" id="index_scendAuth_btn"/>
            </form>
            <script src="/tpass/comm/neu/js/login_second.js"></script>
          </body>
        </html>
    """.trimIndent()

    @Test
    fun parse_liveDeviceChallenge_readsMaskedPhoneAndFormAction() {
        val challenge = CasSecondAuthParser.parse(liveSecondAuthHtml, "https://pass.neu.edu.cn/tpass/login")
        assertTrue(challenge.isPresent)
        assertEquals("*******9060", challenge.maskedPhone)
        assertEquals(
            "https://pass.neu.edu.cn/tpass/login?service=https%3A%2F%2Fecode.neu.edu.cn%2Fecode%2Fapi%2Fsso%2Flogin",
            challenge.formAction,
        )
        assertEquals("LT-SECOND-1", challenge.fields["lt"])
        assertEquals("e2s1", challenge.fields["execution"])
        assertEquals("/tpass/code?0.12", challenge.captchaSrc)
        assertTrue(challenge.needsGraphicCaptcha)
        assertTrue(challenge.needsSmsCode)
    }

    @Test
    fun parse_passwordLoginPage_isNotAChallenge() {
        val html = """
            <form id="loginForm" action="/tpass/login">
              <input id="un"/><input id="pd"/>
            </form>
            <script src="/tpass/comm/neu/js/login_neu.js?v=20260302"></script>
        """.trimIndent()
        val challenge = CasSecondAuthParser.parse(html, "https://pass.neu.edu.cn/tpass/login")
        assertFalse(challenge.isPresent)
        assertNull(challenge.maskedPhone)
    }

    @Test
    fun sendCodeRequest_postsGraphicCaptchaWithMobileMethod() {
        val request = CasSecondAuthParser.sendCodeRequest(
            pageUrl = "https://pass.neu.edu.cn/tpass/login?service=abc",
            graphicCaptcha = "0259",
        )
        assertEquals("https://pass.neu.edu.cn/tpass/secondAuthCode", request.url)
        assertEquals("0259", request.fields["code"])
        assertEquals("mobile", request.fields["method"])
    }

    @Test
    fun submitSmsRequest_keepsHiddenFieldsAndSmsCode() {
        val challenge = CasSecondAuthParser.parse(liveSecondAuthHtml, "https://pass.neu.edu.cn/tpass/login")
        val request = CasSecondAuthParser.submitSmsRequest(challenge, smsCode = "210908")
        assertEquals(challenge.formAction, request.url)
        assertEquals("210908", request.fields["authCode"])
        assertEquals("LT-SECOND-1", request.fields["lt"])
        assertEquals("e2s1", request.fields["execution"])
        assertEquals("submit", request.fields["_eventId"])
    }
}
