package com.neko.neuecode.ui.screen.paycode

object PayCodeRefreshPolicy {
    private const val AUTO_FETCH_LEAD_MS = 3_000L
    private const val AUTO_FETCH_MIN_DELAY_MS = 5_000L

    fun canRefreshPayCode(awaitingSms: Boolean, isRefreshing: Boolean = false): Boolean {
        if (awaitingSms || isRefreshing) return false
        return true
    }

    fun shouldContinueAutoFetch(awaitingSms: Boolean): Boolean = !awaitingSms

    fun shouldRetryAfterSms(awaitingSms: Boolean): Boolean = !awaitingSms

    fun nextAutoFetchDelayMs(
        success: Boolean,
        ttlSeconds: Int?,
        awaitingSms: Boolean,
    ): Long? {
        if (awaitingSms || !success) return null
        val ttlMs = (ttlSeconds ?: 0).coerceAtLeast(0) * 1_000L
        if (ttlMs <= 0L) return null
        return (ttlMs - AUTO_FETCH_LEAD_MS).coerceAtLeast(AUTO_FETCH_MIN_DELAY_MS)
    }
}
