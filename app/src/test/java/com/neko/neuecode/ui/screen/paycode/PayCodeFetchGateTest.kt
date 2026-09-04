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
    fun autoRefreshHitsSms_turnsSwitchOffAndKeepsHint() {
        val next = PayCodeFetchGate.afterNeedSms(
            userInitiated = false,
            currentSwitchOn = true,
        )
        assertFalse(next.userSwitchOn)
        assertTrue(next.lockedBySms)
        assertEquals(
            "自动刷新触发了短信验证，已关闭取码开关。请手动打开开关并完成一次取码验证后才能继续自动刷新。",
            next.switchHint,
        )
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
