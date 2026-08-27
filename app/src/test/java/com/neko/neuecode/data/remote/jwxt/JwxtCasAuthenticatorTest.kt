package com.neko.neuecode.data.remote.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.util.Base64
import javax.crypto.Cipher

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
}
