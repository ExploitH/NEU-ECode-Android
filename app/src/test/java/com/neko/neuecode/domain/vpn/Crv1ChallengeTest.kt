package com.neko.neuecode.domain.vpn

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Crv1ChallengeTest {

    @Test
    fun parse_officialDynamicChallengeCookie() {
        val usernameB64 = Base64.getEncoder().encodeToString("20240001".toByteArray(Charsets.UTF_8))
        val cookie = "CRV1:R,E:state-abc:$usernameB64:请输入短信验证码"
        val parsed = Crv1Challenge.parse(cookie)
        requireNotNull(parsed)
        assertEquals("state-abc", parsed.stateId)
        assertEquals("20240001", parsed.username)
        assertEquals("请输入短信验证码", parsed.challengeText)
        assertEquals(cookie, parsed.rawCookie)
        assertTrue(parsed.responseRequired)
        assertTrue(parsed.echo)
        assertFalse(parsed.toString().contains("20240001"))
    }

    @Test
    fun buildPassword_usesOfficialResponseShape() {
        val challenge = Crv1Challenge(
            stateId = "state-abc",
            username = "20240001",
            challengeText = "SMS",
            responseRequired = true,
            echo = false,
        )
        assertEquals("CRV1::state-abc::654321", challenge.buildPassword("654321"))
    }

    @Test
    fun parse_rejectsGarbageAndDoesNotInventRetry() {
        assertNull(Crv1Challenge.parse("AUTH_FAILED"))
        assertNull(Crv1Challenge.parse("CRV1:only-two"))
        assertNull(Crv1Challenge.parse(""))
    }
}
