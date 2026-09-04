package com.neko.neuecode.ui.screen.paycode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                fetchEnabled = true,
            ),
        )
        assertFalse(
            PayCodeRefreshPolicy.canRefreshPayCode(
                awaitingSms = false,
                isRefreshing = false,
                fetchEnabled = false,
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

    @Test
    fun autoLoop_schedulesOnlyOnSuccessfulTtl() {
        assertEquals(
            12_000L,
            PayCodeRefreshPolicy.nextAutoFetchDelayMs(
                success = true,
                ttlSeconds = 15,
                awaitingSms = false,
            ),
        )
        assertEquals(
            5_000L,
            PayCodeRefreshPolicy.nextAutoFetchDelayMs(
                success = true,
                ttlSeconds = 3,
                awaitingSms = false,
            ),
        )
    }

    @Test
    fun autoLoop_stopsForeverOnSms() {
        assertNull(
            PayCodeRefreshPolicy.nextAutoFetchDelayMs(
                success = false,
                ttlSeconds = 15,
                awaitingSms = true,
            ),
        )
        assertFalse(
            PayCodeRefreshPolicy.shouldContinueAutoFetch(awaitingSms = true),
        )
        assertFalse(
            PayCodeRefreshPolicy.shouldRetryAfterSms(awaitingSms = true),
        )
    }

    @Test
    fun autoLoop_doesNotRetryFailedFetchWithoutSms() {
        assertNull(
            PayCodeRefreshPolicy.nextAutoFetchDelayMs(
                success = false,
                ttlSeconds = 15,
                awaitingSms = false,
            ),
        )
    }
}
