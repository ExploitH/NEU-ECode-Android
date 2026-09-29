package com.neko.neuecode.ui.screen.paycode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCodeFetchGateTest {

    @Test
    fun switchOff_blocksFetchAndAutoRefresh() {
        val decision = PayCodeFetchGate.decide(
            moduleEnabled = true,
            userSwitchOn = false,
            awaitingSms = false,
            isRefreshing = false,
        )
        assertFalse(decision.mayFetch)
        assertFalse(decision.mayAutoRefresh)
        assertTrue(decision.showSwitch)
    }

    @Test
    fun switchOn_allowsFetchWhenIdle() {
        val decision = PayCodeFetchGate.decide(
            moduleEnabled = true,
            userSwitchOn = true,
            awaitingSms = false,
            isRefreshing = false,
        )
        assertTrue(decision.mayFetch)
        assertTrue(decision.mayAutoRefresh)
    }

    @Test
    fun autoRefreshHitsSms_turnsAutoRefreshOffAndKeepsHint() {
        val next = PayCodeFetchGate.afterNeedSms(
            userInitiated = false,
            currentSwitchOn = true,
            currentAutoRefresh = true,
        )
        assertTrue(next.userSwitchOn)
        assertFalse(next.autoRefreshOn)
        assertTrue(next.lockedBySms)
        assertEquals(PayCodeFetchGate.AUTO_SMS_HINT, next.switchHint)
        assertFalse(
            PayCodeFetchGate.decide(
                moduleEnabled = true,
                userSwitchOn = next.userSwitchOn,
                awaitingSms = false,
                isRefreshing = false,
                autoRefreshOn = next.autoRefreshOn,
            ).mayAutoRefresh,
        )
    }

    @Test
    fun userInitiatedNeedSms_keepsAutoRefreshChoice() {
        val next = PayCodeFetchGate.afterNeedSms(
            userInitiated = true,
            currentSwitchOn = true,
            currentAutoRefresh = false,
        )
        assertFalse(next.autoRefreshOn)
        assertTrue(PayCodeFetchGate.afterSuccess(currentAutoRefresh = true).autoRefreshOn)
    }

    @Test
    fun userInitiatedNeedSms_keepsSwitchOnSoUserCanFinishChallenge() {
        val next = PayCodeFetchGate.afterNeedSms(
            userInitiated = true,
            currentSwitchOn = true,
        )
        assertTrue(next.userSwitchOn)
        assertTrue(next.lockedBySms)
        assertTrue(next.switchHint.contains("图形验证码"))
        assertTrue(next.switchHint.contains("短信验证码"))
    }

    @Test
    fun successfulManualFetch_clearsSmsLock() {
        val next = PayCodeFetchGate.afterSuccess()
        assertFalse(next.lockedBySms)
        assertEquals("", next.switchHint)
    }
}
