package com.neko.neuecode.ui.screen.paycode

import com.neko.neuecode.domain.ecode.PayCode
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
)

object PayCodeHomePresentation {
    const val OPEN_PAY_CODE_LABEL = "打开付款码"

    fun loading(): PayCodeHomeState {
        return PayCodeHomeState(
            status = PayCodeHomeStatus.Loading,
            showOpenPayCodeButton = false,
            showNativeQr = false,
            payload = null,
            ttlSeconds = null,
            syncHint = "正在同步付款码…",
        )
    }

    fun from(result: PayCodeParseResult): PayCodeHomeState {
        return when (result) {
            is PayCodeParseResult.Success -> PayCodeHomeState(
                status = PayCodeHomeStatus.Ready,
                showOpenPayCodeButton = false,
                showNativeQr = true,
                payload = result.code.payload,
                ttlSeconds = result.code.ttlSeconds,
                syncHint = ttlHint(result.code),
            )
            is PayCodeParseResult.Failure -> PayCodeHomeState(
                status = PayCodeHomeStatus.Failed,
                showOpenPayCodeButton = true,
                showNativeQr = false,
                payload = null,
                ttlSeconds = null,
                syncHint = result.message,
            )
        }
    }

    private fun ttlHint(payCode: PayCode): String {
        val ttl = payCode.ttlSeconds
        return if (ttl > 0) "协议取码成功 · ${ttl} 秒内有效" else "协议取码成功"
    }
}
