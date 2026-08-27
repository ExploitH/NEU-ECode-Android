package com.neko.neuecode.data.remote.crypto

import java.util.UUID

object DeviceImei {
    private val hex32 = Regex("^[0-9a-f]{32}$")

    fun normalize(raw: String?): String? {
        val cleaned = raw.orEmpty().replace("-", "").lowercase()
        if (cleaned.isBlank() || cleaned.all { it == '0' }) return null
        return cleaned.takeIf { hex32.matches(it) }
    }

    fun newInstallId(): String = UUID.randomUUID().toString().replace("-", "")
}
