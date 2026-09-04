package com.neko.neuecode.ui.screen.paycode

import com.neko.neuecode.domain.ecode.PayCode
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult

enum class PayCodeHomeStatus {
    Loading,
    Ready,
    Failed,
}

data class PayCodeHomeState(
    val status: PayCodeHomeStatus,
    val showOpenPayCodeButton: Boolean,
    val showNativeQr: Boolean = false,
    val payload: String? = null,
    val ttlSeconds: Int? = null,
    val syncHint: String? = null,
    val fetchEnabled: Boolean = false,
    val switchHint: String? = null,
    val showSmsChallenge: Boolean = false,
    val maskedPhone: String? = null,
)

object PayCodeHomePresentation {
    const val OPEN_PAY_CODE_LABEL = "打开付款码"

    fun loading(
        fetchEnabled: Boolean = false,
        switchHint: String? = null,
    ): PayCodeHomeState {
        return PayCodeHomeState(
            status = PayCodeHomeStatus.Loading,
            showOpenPayCodeButton = false,
            showNativeQr = false,
            payload = null,
            ttlSeconds = null,
            syncHint = "正在同步付款码…",
            fetchEnabled = fetchEnabled,
            switchHint = switchHint,
            showSmsChallenge = false,
        )
    }

    fun idle(
        fetchEnabled: Boolean,
        switchHint: String? = null,
        awaitingSms: Boolean = false,
        maskedPhone: String? = null,
        message: String? = null,
    ): PayCodeHomeState {
        return PayCodeHomeState(
            status = PayCodeHomeStatus.Failed,
            showOpenPayCodeButton = false,
            showNativeQr = false,
            payload = null,
            ttlSeconds = null,
            syncHint = message ?: if (fetchEnabled) "打开开关后才会取码" else "取码开关已关闭，不会自动刷新或取码",
            fetchEnabled = fetchEnabled,
            switchHint = switchHint,
            showSmsChallenge = awaitingSms,
            maskedPhone = maskedPhone,
        )
    }

    fun from(
        result: PayCodeParseResult,
        fetchEnabled: Boolean = true,
        switchHint: String? = null,
        maskedPhone: String? = null,
    ): PayCodeHomeState {
        return when (result) {
            is PayCodeParseResult.Success -> PayCodeHomeState(
                status = PayCodeHomeStatus.Ready,
                showOpenPayCodeButton = false,
                showNativeQr = true,
                payload = result.code.payload,
                ttlSeconds = result.code.ttlSeconds,
                syncHint = ttlHint(result.code),
                fetchEnabled = fetchEnabled,
                switchHint = switchHint,
                showSmsChallenge = false,
                maskedPhone = null,
            )
            is PayCodeParseResult.Failure -> PayCodeHomeState(
                status = PayCodeHomeStatus.Failed,
                showOpenPayCodeButton = result.reason != PayCodeFailure.NeedSms,
                showNativeQr = false,
                payload = null,
                ttlSeconds = null,
                syncHint = result.message,
                fetchEnabled = fetchEnabled,
                switchHint = switchHint,
                showSmsChallenge = result.reason == PayCodeFailure.NeedSms,
                maskedPhone = maskedPhone,
            )
        }
    }

    private fun ttlHint(payCode: PayCode): String {
        val ttl = payCode.ttlSeconds
        return if (ttl > 0) "协议取码成功 · ${ttl} 秒内有效" else "协议取码成功"
    }
}
