package com.neko.neuecode.data.remote.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtCasCryptoTest {

    @Test
    fun extractPublicKeyFromJs_readsConstAssignment() {
        val js = """
            function login(){
            	const publicKeyStr = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnjA28DLKXZzxbKmo9/1WkVLf1mr+wtLXLXt6sC4WiBCtsbzF5ewm7ARZeAdS3iZtqlYPn6IcUoOw42H8nAK/tfFcIb6dZ1K0atn0U39oWCGPzYuKtLJeMuNZiDXVuAXtojrckOjLW9B3gUnaNGLuIx0fYe66l0o9WjU2cGLNZQfiIxs2h00z1EA9IdSnVxiVQWSD+lsP3JZXh2TT287la4Y4603SQNKTK/QvXfcmccwTEd1IW6HwGxD6QrkInBiHisKWxmveN7UDSaQRZ/J97G0YC32pD38WT53izXeK0p/kU/X37VP555um1wVWFvPIuc9I7gMP1+hq5a+X6c++tQIDAQAB";
            	const rsa = new RSAEncryptor(publicKeyStr);
            }
        """.trimIndent()
        val key = JwxtCasCrypto.extractPublicKeyFromJs(js)
        assertTrue(key.startsWith("MIIBIjAN"))
        assertTrue(key.endsWith("IDAQAB"))
        assertEquals(392, key.length)
    }

    @Test
    fun extractPublicKeyFromJs_readsSingleQuotedAndLetAssignment() {
        val js = "let publicKeyStr = 'MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKs2qmEOHBN7PF6O2M5UdvgLcs2tggpQ6gbypkz5mLFmWi8VCwyKM9guLhUu0TvolcrVvS9G51BOvJSKAsclJ3sCAwEAAQ==';"
        val key = JwxtCasCrypto.extractPublicKeyFromJs(js)
        assertTrue(key.startsWith("MFwwDQYJ"))
    }

    @Test
    fun looksLikeSmsChallenge_detectsDeviceSecondFactorPage() {
        val html = """
            <html><body>
              <div>二次认证</div>
              <input id="mcode" />
              <a id="sendCode">获取验证码</a>
              <p>登录码已发送，请输入验证码</p>
            </body></html>
        """.trimIndent()
        assertTrue(JwxtCasCrypto.looksLikeSmsChallenge(html))
        assertFalse(JwxtCasCrypto.looksLikeSmsChallenge("<form id=\"loginForm\"></form>"))
    }

    @Test
    fun looksLikeSmsChallenge_ignoresHiddenPhoneTemplateOnPasswordPage() {
        val html = """
            <form id="loginForm" action="/tpass/login">
              <input id="un" /><input id="pd" />
            </form>
            <div id="template_phone" style="display:none;">
              <input id="mcode" />
              <a id="sendCode">获取验证码</a>
            </div>
            <script src="/tpass/comm/neu/js/login_neu.js?v=20260302"></script>
        """.trimIndent()
        assertFalse(JwxtCasCrypto.looksLikeSmsChallenge(html))
    }

    @Test
    fun extractLoginScriptUrl_findsVersionedLoginNeu() {
        val html = """
            <script src="/tpass/comm/neu/js/rsa.js"></script>
            <script type="text/javascript" src="/tpass/comm/neu/js/login_neu.js?v=20260302"></script>
        """.trimIndent()
        assertEquals(
            "https://pass.neu.edu.cn/tpass/comm/neu/js/login_neu.js?v=20260302",
            JwxtCasCrypto.extractLoginScriptUrl(html, "https://pass.neu.edu.cn/tpass/login"),
        )
    }
}
