package com.neko.neuecode.ui.screen.intranet

/**
 * Launch an already-installed OpenVPN client as a *fallback*.
 * In-app path uses official OpenVPN/openvpn3 (MPL-2.0) + our VpnService.
 * Do **not** vendor GPL `ics-openvpn` sources.
 */
object OpenVpnLaunchIntent {
    val CANDIDATE_PACKAGES: List<String> = listOf(
        "net.openvpn.openvpn",
        "de.blinkt.openvpn",
    )

    fun firstAvailable(installedPackages: Collection<String>): String? {
        return CANDIDATE_PACKAGES.firstOrNull { it in installedPackages }
    }
}
