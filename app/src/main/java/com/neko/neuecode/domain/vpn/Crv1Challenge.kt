package com.neko.neuecode.domain.vpn

import java.util.Base64

/**
 * Official OpenVPN 3 dynamic challenge (`openvpn/auth/cr.hpp`):
 *
 * `CRV1:<FLAGS>:<STATE_ID>:<BASE64_USERNAME>:<CHALLENGE_TEXT>`
 * Response password: `CRV1::<STATE_ID>::<RESPONSE_TEXT>`
 */
data class Crv1Challenge(
    val stateId: String,
    val username: String,
    val challengeText: String,
    val responseRequired: Boolean,
    val echo: Boolean,
) {
    fun buildPassword(response: String): String = "CRV1::$stateId::$response"

    override fun toString(): String {
        return "Crv1Challenge(stateId=[REDACTED], username=[REDACTED], echo=$echo, responseRequired=$responseRequired)"
    }

    companion object {
        fun parse(cookie: String): Crv1Challenge? {
            if (!cookie.startsWith("CRV1:")) return null
            val parts = cookie.split(":", limit = 5)
            if (parts.size != 5 || parts[0] != "CRV1") return null
            if (parts[2].isBlank()) return null
            val flags = parts[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val username = try {
                String(Base64.getDecoder().decode(parts[3]), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                return null
            }
            return Crv1Challenge(
                stateId = parts[2],
                username = username,
                challengeText = parts[4],
                responseRequired = "R" in flags,
                echo = "E" in flags,
            )
        }
    }
}
