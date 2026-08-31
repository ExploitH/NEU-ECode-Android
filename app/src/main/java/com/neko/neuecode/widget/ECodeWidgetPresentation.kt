package com.neko.neuecode.widget

object ECodeWidgetPresentation {
    const val ACTION_REFRESH_QR = "com.neko.neuecode.widget.ACTION_REFRESH_QR"
    const val ACTION_REFRESH_BALANCE = "com.neko.neuecode.widget.ACTION_REFRESH_BALANCE"

    fun showBalances(showBalance: Boolean): Boolean = showBalance

    fun qrStatus(success: Boolean, ttlSeconds: Int?): String {
        return if (success) {
            val ttl = ttlSeconds?.takeIf { it > 0 }
            if (ttl != null) "付款码 · ${ttl}秒内有效" else "付款码已更新"
        } else {
            "付款码功能暂时停用"
        }
    }

    fun bodyClickAction(): String = ACTION_REFRESH_QR

    fun refreshClickAction(): String = ACTION_REFRESH_BALANCE
}
