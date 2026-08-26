package com.neko.neuecode.data.remote.ecode

import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ECodePayCodeParserTest {

    private val nowEpochMs = 1_710_000_000_000L

    @Test
    fun parse_successExtractsPayloadTtlAndExpiresAt() {
        val json = """
            {"e":"0","m":"ok","d":{"payload":"NEU-PAY-FIXTURE-001","ttlSeconds":90,"expiresAtEpochMs":1710000090000}}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val success = result as PayCodeParseResult.Success
        assertEquals("NEU-PAY-FIXTURE-001", success.code.payload)
        assertEquals(90, success.code.ttlSeconds)
        assertEquals(1_710_000_090_000L, success.code.expiresAtEpochMs)
    }

    @Test
    fun parse_missingExpiresAtUsesNowPlusTtl() {
        val json = """
            {"e":"0","m":"ok","d":{"payload":"NEU-PAY-FIXTURE-001","ttlSeconds":90}}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val success = result as PayCodeParseResult.Success
        assertEquals("NEU-PAY-FIXTURE-001", success.code.payload)
        assertEquals(90, success.code.ttlSeconds)
        assertEquals(nowEpochMs + 90_000L, success.code.expiresAtEpochMs)
    }

    @Test
    fun parse_missingTtlComputesFromExpiresAtFlooringAtZero() {
        val json = """
            {"e":"0","m":"ok","d":{"payload":"NEU-PAY-FIXTURE-001","expiresAtEpochMs":1710000090000}}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val success = result as PayCodeParseResult.Success
        assertEquals(90, success.code.ttlSeconds)
        assertEquals(1_710_000_090_000L, success.code.expiresAtEpochMs)
    }

    @Test
    fun parse_successEnvelopeButAlreadyExpired_returnsExpired() {
        val json = """
            {"e":"0","m":"ok","d":{"payload":"NEU-PAY-FIXTURE-001","ttlSeconds":90,"expiresAtEpochMs":1710000000000}}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.Expired, failure.reason)
    }

    @Test
    fun parse_blankPayload_returnsProtocolError() {
        val json = """
            {"e":"0","m":"ok","d":{"payload":"   ","ttlSeconds":90,"expiresAtEpochMs":1710000090000}}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.ProtocolError, failure.reason)
    }

    @Test
    fun parse_weiDengLuMessage_returnsNeedRelogin() {
        val json = """
            {"e":"1","m":"未登录","d":[]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.NeedRelogin, failure.reason)
    }

    @Test
    fun parse_needReloginToken_returnsNeedRelogin() {
        val json = """
            {"e":"1","m":"NEED_RELOGIN","d":[]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.NeedRelogin, failure.reason)
    }

    @Test
    fun parse_http401Message_returnsUnauthenticated() {
        val json = """
            {"e":"1","m":"401 Unauthenticated","d":[]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.Unauthenticated, failure.reason)
    }

    @Test
    fun parse_campusNetMessage_returnsNeedCampusNet() {
        val json = """
            {"e":"1","m":"请连接校园网","d":[]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.NeedCampusNet, failure.reason)
    }

    @Test
    fun parse_expiredMessage_returnsExpired() {
        val json = """
            {"e":"1","m":"付款码已过期 ttl","d":[]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.Expired, failure.reason)
    }

    @Test
    fun parse_unknownErrorEnvelope_returnsProtocolError() {
        val json = """
            {"e":"1","m":"服务暂时不可用","d":[]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.ProtocolError, failure.reason)
    }

    @Test
    fun parse_garbageJson_returnsUnknown() {
        val result = ECodePayCodeParser.parse("{not-json", nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.Unknown, failure.reason)
    }

    @Test
    fun parse_dAsJsonStringOfInnerObject_stillSucceeds() {
        val json = """
            {"e":"0","m":"ok","d":"{\"payload\":\"NEU-PAY-FIXTURE-001\",\"ttlSeconds\":90,\"expiresAtEpochMs\":1710000090000}"}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val success = result as PayCodeParseResult.Success
        assertEquals("NEU-PAY-FIXTURE-001", success.code.payload)
        assertEquals(90, success.code.ttlSeconds)
        assertEquals(1_710_000_090_000L, success.code.expiresAtEpochMs)
    }

    @Test
    fun parse_opaqueCiphertextD_returnsProtocolErrorWithoutDecrypting() {
        val json = """
            {"e":"0","m":"ok","d":"QUJDRUVGR0hJSktMTU5PUFFSU1RVVldYWVo="}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.ProtocolError, failure.reason)
        assertTrue(failure.message.orEmpty().contains("decrypt", ignoreCase = true))
    }

    @Test
    fun parse_jsonApiSuccessExtractsQrCodeAndQrInvalidTime() {
        val json = """
            {"data":[{"type":null,"attributes":{"qrCode":"NEU-PAY-FIXTURE-001","createTime":"1710000000000","qrInvalidTime":"1710000090000"}}]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val success = result as PayCodeParseResult.Success
        assertEquals("NEU-PAY-FIXTURE-001", success.code.payload)
        assertEquals(1_710_000_090_000L, success.code.expiresAtEpochMs)
        assertEquals(90, success.code.ttlSeconds)
    }

    @Test
    fun parse_jsonApiExpiredQrInvalidTime_returnsExpired() {
        val json = """
            {"data":[{"type":null,"attributes":{"qrCode":"NEU-PAY-FIXTURE-001","createTime":"1709999910000","qrInvalidTime":"1710000000000"}}]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.Expired, failure.reason)
    }

    @Test
    fun parse_jsonApiBlankQrCode_returnsProtocolError() {
        val json = """
            {"data":[{"type":null,"attributes":{"qrCode":"   ","createTime":"1710000000000","qrInvalidTime":"1710000090000"}}]}
        """.trimIndent()

        val result = ECodePayCodeParser.parse(json, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertEquals(PayCodeFailure.ProtocolError, failure.reason)
    }

    @Test
    fun parse_htmlLoginPage_returnsUnauthenticatedOrNeedRelogin() {
        val html = """
            <!DOCTYPE html><html><head><title>登录</title></head><body>请先登录</body></html>
        """.trimIndent()

        val result = ECodePayCodeParser.parse(html, nowEpochMs)

        val failure = result as PayCodeParseResult.Failure
        assertTrue(
            failure.reason == PayCodeFailure.NeedRelogin ||
                failure.reason == PayCodeFailure.Unauthenticated,
        )
    }
}
