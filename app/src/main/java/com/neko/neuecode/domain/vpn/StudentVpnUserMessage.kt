package com.neko.neuecode.domain.vpn

object StudentVpnUserMessage {
    fun from(name: String, info: String): String {
        val combined = "$name $info"
        return when {
            name.equals("CORE_MISSING", ignoreCase = true) ||
                combined.contains("未编入") -> "官方 OpenVPN 3 核心未编入本构建"
            combined.contains("TUN_SETUP_FAILED", ignoreCase = true) ||
                combined.contains("tun-null", ignoreCase = true) ||
                combined.contains("tun_null", ignoreCase = true) ->
                "隧道建立失败（未走系统 VpnService TunBuilder）"
            combined.contains("option_error", ignoreCase = true) ||
                combined.contains("eval_config", ignoreCase = true) ||
                combined.contains("ERR_INVALID", ignoreCase = true) ->
                "学生 VPN 配置无法被官方 OpenVPN 3 解析"
            combined.contains("AUTH_FAILED", ignoreCase = true) ->
                "认证失败，请重新获取短信验证码后再试一次"
            combined.contains("校园") ||
                combined.contains("network", ignoreCase = true) ||
                combined.contains("timeout", ignoreCase = true) ||
                combined.contains("unreachable", ignoreCase = true) ->
                "校园 VPN 入口不可达"
            else -> "连接失败"
        }
    }
}
