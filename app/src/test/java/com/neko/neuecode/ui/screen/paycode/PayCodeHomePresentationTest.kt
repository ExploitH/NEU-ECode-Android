package com.neko.neuecode.ui.screen.paycode

import com.neko.neuecode.domain.ecode.PayCode
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCodeHomePresentationTest {

    @Test
    fun protocolReady_doesNotShowNativeQrOrOpenButton() {
        val state = PayCodeHomePresentation.from(
            PayCodeParseResult.Success(
                PayCode(
                    payload = "NEU-PAY-FIXTURE-001",
                    expiresAtEpochMs = 1_710_000_090_000L,
                    ttlSeconds = 90,
                ),
            ),
        )

        assertEquals(PayCodeHomeStatus.Ready, state.status)
        assertFalse(state.showNativeQr)
        assertFalse(state.showOpenPayCodeButton)
        assertEquals("打开付款码", PayCodeHomePresentation.OPEN_PAY_CODE_LABEL)
    }

    @Test
    fun protocolFailed_showsOpenButtonAndNeverNativeQr() {
        val reasons = listOf(
            PayCodeFailure.Expired,
            PayCodeFailure.Unauthenticated,
            PayCodeFailure.NeedCampusNet,
            PayCodeFailure.NeedRelogin,
            PayCodeFailure.ProtocolError,
            PayCodeFailure.Unknown,
        )

        reasons.forEach { reason ->
            val state = PayCodeHomePresentation.from(
                PayCodeParseResult.Failure(reason, "fixture"),
            )
            assertEquals(PayCodeHomeStatus.Failed, state.status)
            assertTrue(reason.name, state.showOpenPayCodeButton)
            assertFalse(reason.name, state.showNativeQr)
        }
    }

    @Test
    fun loading_hasNoNativeQrAndNoOpenButton() {
        val state = PayCodeHomePresentation.loading()
        assertEquals(PayCodeHomeStatus.Loading, state.status)
        assertFalse(state.showNativeQr)
        assertFalse(state.showOpenPayCodeButton)
    }
}
