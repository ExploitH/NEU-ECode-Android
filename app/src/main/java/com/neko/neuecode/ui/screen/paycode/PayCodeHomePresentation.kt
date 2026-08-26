package com.neko.neuecode.ui.screen.paycode

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
    val syncHint: String? = null,
)

object PayCodeHomePresentation {
    const val OPEN_PAY_CODE_LABEL = "打开付款码"

    fun loading(): PayCodeHomeState {
        return PayCodeHomeState(
            status = PayCodeHomeStatus.Loading,
            showOpenPayCodeButton = false,
            showNativeQr = false,
            syncHint = "正在同步付款码…",
        )
    }

    fun from(result: PayCodeParseResult): PayCodeHomeState {
        return when (result) {
            is PayCodeParseResult.Success -> PayCodeHomeState(
                status = PayCodeHomeStatus.Ready,
                showOpenPayCodeButton = false,
                showNativeQr = false,
                syncHint = "小组件可用",
            )
            is PayCodeParseResult.Failure -> PayCodeHomeState(
                status = PayCodeHomeStatus.Failed,
                showOpenPayCodeButton = true,
                showNativeQr = false,
                syncHint = result.message,
            )
        }
    }
}
