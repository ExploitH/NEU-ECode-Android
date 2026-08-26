package com.neko.neuecode.ui.navigation

/**
 * Frozen three-tab information architecture.
 *
 * Bottom bar is pay / schedule / me only. Recharge, intranet, and the eCode
 * WebView fallback are secondary routes, never bottom-bar destinations.
 */
object MainDestinations {
    const val PAY = "pay"
    const val SCHEDULE = "schedule"
    const val ME = "me"
    const val RECHARGE = "recharge"
    const val INTRANET = "intranet"
    const val ECODE_WEBVIEW = "ecodeWebView"

    const val LABEL_PAY = "付款码"
    const val LABEL_SCHEDULE = "课表"
    const val LABEL_ME = "我的"

    val bottomBar: List<String> = listOf(PAY, SCHEDULE, ME)
    val secondary: List<String> = listOf(RECHARGE, INTRANET, ECODE_WEBVIEW)

    /** Route used when protocol pay-code fetch fails and user taps 「打开付款码」. */
    const val openPayCodeRoute: String = ECODE_WEBVIEW

    fun isBottomBar(route: String): Boolean = route in bottomBar
    fun isSecondary(route: String): Boolean = route in secondary
}
