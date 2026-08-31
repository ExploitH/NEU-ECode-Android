package com.neko.neuecode.ui.screen.paycode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCodeRefreshPolicyTest {

    @Test
    fun smsChallenge_blocksPayCodeRefresh() {
        assertFalse(
            PayCodeRefreshPolicy.canRefreshPayCode(
                awaitingSms = true,
                isRefreshing = false,
            ),
        )
        assertFalse(
            PayCodeRefreshPolicy.canRefreshPayCode(
                awaitingSms = true,
                isRefreshing = true,
            ),
        )
    }

    @Test
    fun idleWithoutSms_allowsRefresh() {
        assertTrue(
            PayCodeRefreshPolicy.canRefreshPayCode(
                awaitingSms = false,
                isRefreshing = false,
            ),
        )
    }

    @Test
    fun inFlightRefresh_isBlockedEvenWithoutSms() {
        assertFalse(
            PayCodeRefreshPolicy.canRefreshPayCode(
                awaitingSms = false,
                isRefreshing = true,
            ),
        )
    }
}
