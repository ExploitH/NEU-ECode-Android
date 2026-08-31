package com.neko.neuecode.ui.screen.paycode

object PayCodeRefreshPolicy {
    fun canRefreshPayCode(awaitingSms: Boolean, isRefreshing: Boolean = false): Boolean {
        if (isRefreshing) return false
        return !awaitingSms
    }
}
