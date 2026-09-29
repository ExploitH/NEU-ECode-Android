package com.neko.neuecode.ui.screen.paycode

data class PayCodeFetchDecision(
    val mayFetch: Boolean,
    val mayAutoRefresh: Boolean,
    val showSwitch: Boolean,
)

data class PayCodeSwitchSnapshot(
    val userSwitchOn: Boolean,
    val lockedBySms: Boolean,
    val switchHint: String,
    /** 「自动刷新二维码」 after this step. */
    val autoRefreshOn: Boolean = true,
)

object PayCodeFetchGate {
    const val AUTO_SMS_HINT =
        "自动刷新触发了短信验证，已关闭自动刷新。完成验证后可手动刷新，需要时再重新打开自动刷新。"
    const val MANUAL_SMS_HINT =
        "当前设备需要图形验证码和短信验证码。请完成验证后再取码；验证完成前请不要反复刷新。"

    fun decide(
        moduleEnabled: Boolean,
        userSwitchOn: Boolean,
        awaitingSms: Boolean,
        isRefreshing: Boolean,
        autoRefreshOn: Boolean = true,
    ): PayCodeFetchDecision {
        val showSwitch = moduleEnabled
        val mayFetch = moduleEnabled && userSwitchOn && !awaitingSms && !isRefreshing
        val mayAutoRefresh = moduleEnabled && userSwitchOn && autoRefreshOn && !awaitingSms
        return PayCodeFetchDecision(
            mayFetch = mayFetch,
            mayAutoRefresh = mayAutoRefresh,
            showSwitch = showSwitch,
        )
    }

    /**
     * An SMS challenge always locks fetching until verified. When the automatic
     * loop hit it, auto refresh is switched off so it never re-triggers SMS on
     * its own; the e码通 switch stays on so the challenge can be finished in place.
     */
    fun afterNeedSms(
        userInitiated: Boolean,
        currentSwitchOn: Boolean,
        currentAutoRefresh: Boolean = true,
    ): PayCodeSwitchSnapshot {
        return if (userInitiated) {
            PayCodeSwitchSnapshot(
                userSwitchOn = currentSwitchOn,
                lockedBySms = true,
                switchHint = MANUAL_SMS_HINT,
                autoRefreshOn = currentAutoRefresh,
            )
        } else {
            PayCodeSwitchSnapshot(
                userSwitchOn = currentSwitchOn,
                lockedBySms = true,
                switchHint = AUTO_SMS_HINT,
                autoRefreshOn = false,
            )
        }
    }

    fun afterSuccess(currentAutoRefresh: Boolean = true): PayCodeSwitchSnapshot {
        return PayCodeSwitchSnapshot(
            userSwitchOn = true,
            lockedBySms = false,
            switchHint = "",
            autoRefreshOn = currentAutoRefresh,
        )
    }
}
