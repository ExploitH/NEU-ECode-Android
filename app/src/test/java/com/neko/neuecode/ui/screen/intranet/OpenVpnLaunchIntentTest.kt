package com.neko.neuecode.ui.screen.intranet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnLaunchIntentTest {

    @Test
    fun candidatePackages_doNotIncludeGplSources() {
        assertEquals(
            listOf(
                "net.openvpn.openvpn",
                "de.blinkt.openvpn",
            ),
            OpenVpnLaunchIntent.CANDIDATE_PACKAGES,
        )
        assertTrue(OpenVpnLaunchIntent.CANDIDATE_PACKAGES.none { it.contains("ics") })
    }

    @Test
    fun firstAvailable_prefersOfficialClient() {
        val chosen = OpenVpnLaunchIntent.firstAvailable(
            listOf("com.other.app", "de.blinkt.openvpn", "net.openvpn.openvpn"),
        )
        assertEquals("net.openvpn.openvpn", chosen)
    }

    @Test
    fun firstAvailable_returnsNullWhenNoneInstalled() {
        assertEquals(null, OpenVpnLaunchIntent.firstAvailable(emptyList()))
        assertEquals(null, OpenVpnLaunchIntent.firstAvailable(listOf("com.android.chrome")))
    }
}
