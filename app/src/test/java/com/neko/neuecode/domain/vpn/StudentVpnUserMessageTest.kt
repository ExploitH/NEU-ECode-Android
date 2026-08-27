package com.neko.neuecode.domain.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentVpnUserMessageTest {

    @Test
    fun mapsCoreMissing() {
        assertEquals(
            "官方 OpenVPN 3 核心未编入本构建",
            StudentVpnUserMessage.from("CORE_MISSING", "native library missing"),
        )
    }

    @Test
    fun mapsTunNullHint() {
        assertEquals(
            "隧道建立失败（未走系统 VpnService TunBuilder）",
            StudentVpnUserMessage.from(
                "CONNECT_ERROR",
                "TUN_SETUP_FAILED: tun-null used instead of builder",
            ),
        )
    }

    @Test
    fun mapsEvalConfigErrorWithoutLeakingProfile() {
        val message = StudentVpnUserMessage.from(
            "CONNECT_ERROR",
            "option_error: persist-key /root/neu-vpn/auth.txt",
        )
        assertTrue(message.contains("配置"))
        assertFalse(message.contains("/root/neu-vpn"))
        assertFalse(message.contains("auth.txt"))
    }

    @Test
    fun mapsNetworkUnreachable() {
        assertEquals(
            "校园 VPN 入口不可达",
            StudentVpnUserMessage.from("CONNECTION_TIMEOUT", "network unreachable"),
        )
    }
}
