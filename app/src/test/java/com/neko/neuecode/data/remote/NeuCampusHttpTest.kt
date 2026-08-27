package com.neko.neuecode.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuCampusHttpTest {

    @Test
    fun ecodeApi404_isIntermediateLandingNotAuthFailure() {
        assertTrue(NeuCampusHttp.isEcodeIntermediateLanding(404, "ecode.neu.edu.cn"))
        assertFalse(NeuCampusHttp.isEcodeIntermediateLanding(404, "jwxt.neu.edu.cn"))
        assertFalse(NeuCampusHttp.isEcodeIntermediateLanding(401, "ecode.neu.edu.cn"))
    }

    @Test
    fun gatewayStatuses_areRetryable() {
        assertTrue(NeuCampusHttp.isRetryableGateway(502))
        assertTrue(NeuCampusHttp.isRetryableGateway(503))
        assertTrue(NeuCampusHttp.isRetryableGateway(504))
        assertFalse(NeuCampusHttp.isRetryableGateway(200))
        assertFalse(NeuCampusHttp.isRetryableGateway(401))
    }

    @Test
    fun timeoutAnd502_lookLikeCampusTransport() {
        assertTrue(NeuCampusHttp.looksLikeCampusTransport("failed to connect to jwxt.neu.edu.cn"))
        assertTrue(NeuCampusHttp.looksLikeCampusTransport("JWXT model cxmrxnxq failed: HTTP 502"))
        assertTrue(NeuCampusHttp.looksLikeCampusTransport("timeout"))
        assertFalse(NeuCampusHttp.looksLikeCampusTransport("账号或密码错误"))
    }

    @Test
    fun casSsoSkip_jwxtHomeWithoutLoginForm_isAlreadyAuthenticated() {
        assertTrue(
            NeuCampusHttp.casAlreadyAuthenticated(
                host = "jwxt.neu.edu.cn",
                path = "/jwapp/sys/homeapp/index.do",
                code = 200,
                body = "<html><title>教务系统</title></html>",
            ),
        )
        assertFalse(
            NeuCampusHttp.casAlreadyAuthenticated(
                host = "pass.neu.edu.cn",
                path = "/tpass/login",
                code = 200,
                body = "<form id=\"loginForm\"></form>",
            ),
        )
    }

    @Test
    fun personalPortal_keepsZhilinUserAgent() {
        assertEquals(
            NeuCampusHttp.ZHILIN_USER_AGENT,
            NeuCampusHttp.userAgentFor("personal.neu.edu.cn"),
        )
        assertEquals(
            NeuCampusHttp.BROWSER_USER_AGENT,
            NeuCampusHttp.userAgentFor("jwxt.neu.edu.cn"),
        )
        assertEquals(
            NeuCampusHttp.BROWSER_USER_AGENT,
            NeuCampusHttp.userAgentFor("ecode.neu.edu.cn"),
        )
    }

    @Test
    fun existingZhilinHeader_isNotOverwritten() {
        assertTrue(NeuCampusHttp.shouldKeepExistingHeader(NeuCampusHttp.ZHILIN_USER_AGENT))
        assertFalse(NeuCampusHttp.shouldKeepExistingHeader(null))
        assertFalse(NeuCampusHttp.shouldKeepExistingHeader(""))
    }
}
