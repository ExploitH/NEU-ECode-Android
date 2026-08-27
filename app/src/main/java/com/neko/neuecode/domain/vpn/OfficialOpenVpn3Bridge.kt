package com.neko.neuecode.domain.vpn

/**
 * Thin façade over official OpenVPN 3 (`OpenVPN/openvpn3`, MPL-2.0).
 * Implementations must not wrap schwabe/ics-openvpn.
 */
interface OfficialOpenVpn3Bridge {
    val available: Boolean
    val engineName: String

    fun connect(
        sanitizedProfile: String,
        username: String,
        password: String,
        challengeResponse: String?,
        challengeCookie: String? = null,
        listener: Listener,
    )

    fun disconnect()

    interface Listener {
        fun onEvent(name: String, info: String, error: Boolean, fatal: Boolean)
        fun onLog(line: String)
        fun establishTun(config: TunConfig): Int = -1
        fun protectSocket(fd: Int): Boolean = false
    }

    data class TunConfig(
        val sessionName: String,
        val ipv4: String,
        val ipv4Prefix: Int,
        val ipv4Gateway: String,
        val ipv6: String,
        val ipv6Prefix: Int,
        val dns: List<String>,
        val routes4: List<String>,
        val routes6: List<String>,
        val mtu: Int,
    )
}
