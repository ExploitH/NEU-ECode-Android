package com.neko.neuecode.data.vpn

import com.neko.neuecode.domain.vpn.OfficialOpenVpn3Bridge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default until the official OpenVPN 3 JNI/SWIG client is compiled
 * with the NDK. Fail closed — never silently fall back to ics-openvpn.
 */
@Singleton
class UnbundledOfficialOpenVpn3Bridge @Inject constructor() : OfficialOpenVpn3Bridge {
    override val available: Boolean = false
    override val engineName: String = "OpenVPN/openvpn3 (MPL-2.0, not bundled in this APK)"

    override fun connect(
        sanitizedProfile: String,
        username: String,
        password: String,
        challengeResponse: String?,
        challengeCookie: String?,
        listener: OfficialOpenVpn3Bridge.Listener,
    ) {
        listener.onEvent(
            name = "CORE_MISSING",
            info = "官方 OpenVPN 3 核心未编入本构建",
            error = true,
            fatal = true,
        )
    }

    override fun disconnect() = Unit
}
