package com.neko.neuecode.domain.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentVpnProfileSanitizerTest {

    private val fixture = """
        client
        dev tun
        proto tcp
        remote stu.vpnhost.neu.edu.cn 80
        remote stu.vpnhost.neu.edu.cn 443
        remote-random
        auth-user-pass /tmp/host-only-secret.txt
        redirect-gateway def1
        remote-cert-tls server
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBfixtureNotARealCert
        -----END CERTIFICATE-----
        </ca>
        <tls-auth>
        -----BEGIN OpenVPN Static key V1-----
        deadbeef
        -----END OpenVPN Static key V1-----
        </tls-auth>
    """.trimIndent()

    @Test
    fun sanitize_forcesSplitTunnelAndStripsAuthFilePath() {
        val sanitized = StudentVpnProfileSanitizer.sanitize(fixture)

        assertTrue(sanitized.contains("remote stu.vpnhost.neu.edu.cn 80"))
        assertTrue(sanitized.contains("""pull-filter ignore "redirect-gateway""""))
        assertFalse(sanitized.contains("redirect-gateway def1"))
        assertFalse(sanitized.contains("/tmp/host-only-secret.txt"))
        assertFalse(sanitized.contains("auth-user-pass /"))
        assertTrue(sanitized.lines().any { it.trim() == "auth-user-pass" })
    }

    @Test
    fun redactedLog_hidesInlineSecrets() {
        val sanitized = StudentVpnProfileSanitizer.sanitize(fixture)
        val redacted = StudentVpnProfileSanitizer.redactedForLog(sanitized)

        assertFalse(redacted.contains("MIIBfixtureNotARealCert"))
        assertFalse(redacted.contains("deadbeef"))
        assertTrue(redacted.contains("[REDACTED]"))
        assertTrue(redacted.contains("stu.vpnhost.neu.edu.cn"))
    }

    @Test
    fun inlineCredentials_doNotLeakIntoRedactedLog() {
        val inlined = StudentVpnProfileSanitizer.inlineUserPass(
            StudentVpnProfileSanitizer.sanitize(fixture),
            username = "20240001",
            password = "example-password",
        )
        val redacted = StudentVpnProfileSanitizer.redactedForLog(inlined)
        assertFalse(redacted.contains("example-password"))
        assertFalse(redacted.contains("20240001"))
        assertTrue(inlined.contains("<auth-user-pass>"))
    }
}
