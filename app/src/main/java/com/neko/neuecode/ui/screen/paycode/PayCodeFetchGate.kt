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
)

object PayCodeFetchGate {
    const val AUTO_SMS_HINT =
        "自动刷新触发了短信验证，已关闭取码开关。请手动打开开关并完成一次取码验证后才能继续自动刷新。"
    const val MANUAL_SMS_HINT =
        "当前设备需要图形验证码和短信验证码。请完成验证后再取码；验证完成前请不要反复刷新。"

    fun decide(
        moduleEnabled: Boolean,
        userSwitchOn: Boolean,
        awaitingSms: Boolean,
        isRefreshing: Boolean,
    ): PayCodeFetchDecision {
        val showSwitch = moduleEnabled
        val mayFetch = moduleEnabled && userSwitchOn && !awaitingSms && !isRefreshing
        val mayAutoRefresh = moduleEnabled && userSwitchOn && !awaitingSms
        return PayCodeFetchDecision(
            mayFetch = mayFetch,
            mayAutoRefresh = mayAutoRefresh,
            showSwitch = showSwitch,
        )
    }

    fun afterNeedSms(
        userInitiated: Boolean,
        currentSwitchOn: Boolean,
    ): PayCodeSwitchSnapshot {
        return if (userInitiated) {
            PayCodeSwitchSnapshot(
                userSwitchOn = currentSwitchOn,
                lockedBySms = true,
                switchHint = MANUAL_SMS_HINT,
            )
        } else {
            PayCodeSwitchSnapshot(
                userSwitchOn = false,
                lockedBySms = true,
                switchHint = AUTO_SMS_HINT,
            )
        }
    }

    fun afterSuccess(): PayCodeSwitchSnapshot {
        return PayCodeSwitchSnapshot(
            userSwitchOn = true,
            lockedBySms = false,
            switchHint = "",
        )
    }
}
