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
        assertTrue(next.userSwitchOn)
        assertFalse(next.autoRefreshOn)
        assertTrue(next.lockedBySms)
        assertEquals(PayCodeFetchGate.AUTO_SMS_HINT, next.switchHint)
        assertFalse(
            PayCodeRefreshPolicy.shouldContinueAutoFetch(
                awaitingSms = true,
                fetchEnabled = next.userSwitchOn,
                autoRefresh = next.autoRefreshOn,
            ),
        )
        // Even once the SMS lock clears, the loop stays off until the user re-enables it.
        assertFalse(
            PayCodeRefreshPolicy.shouldContinueAutoFetch(
                awaitingSms = false,
                fetchEnabled = next.userSwitchOn,
                autoRefresh = next.autoRefreshOn,
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
