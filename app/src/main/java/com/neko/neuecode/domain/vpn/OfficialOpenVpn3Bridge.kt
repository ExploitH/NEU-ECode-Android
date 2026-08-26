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
        listener: Listener,
    )

    fun disconnect()

    interface Listener {
        fun onEvent(name: String, info: String, error: Boolean, fatal: Boolean)
        fun onLog(line: String)
    }
}
