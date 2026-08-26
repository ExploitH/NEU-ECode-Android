package com.neko.neuecode.ui.screen.intranet

/**
 * Launch an already-installed OpenVPN client. Do **not** vendor GPL
 * `ics-openvpn` sources into this Apache-2.0 tree.
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
