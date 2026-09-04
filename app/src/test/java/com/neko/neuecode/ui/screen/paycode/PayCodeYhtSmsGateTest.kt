package com.neko.neuecode.ui.screen.paycode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCodeYhtSmsGateTest {

    @Test
    fun autoBalanceSms_usesSamePauseAsPayCodeSms() {
        val next = PayCodeFetchGate.afterNeedSms(
            userInitiated = false,
            currentSwitchOn = true,
        )
        assertFalse(next.userSwitchOn)
        assertTrue(next.lockedBySms)
        assertEquals(PayCodeFetchGate.AUTO_SMS_HINT, next.switchHint)
        assertFalse(
            PayCodeRefreshPolicy.shouldContinueAutoFetch(
                awaitingSms = true,
                fetchEnabled = next.userSwitchOn,
            ),
        )
    }

    @Test
    fun manualBalanceSms_keepsSwitchOn() {
        val next = PayCodeFetchGate.afterNeedSms(
            userInitiated = true,
            currentSwitchOn = true,
        )
        assertTrue(next.userSwitchOn)
        assertTrue(next.lockedBySms)
        assertTrue(PayCodeFetchGate.decide(
            moduleEnabled = true,
            userSwitchOn = next.userSwitchOn,
            awaitingSms = true,
            isRefreshing = false,
        ).showSwitch)
        assertFalse(
            PayCodeRefreshPolicy.canRefreshPayCode(
                awaitingSms = true,
                fetchEnabled = true,
            ),
        )
    }
}
