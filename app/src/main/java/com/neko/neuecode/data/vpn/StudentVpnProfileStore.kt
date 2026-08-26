package com.neko.neuecode.data.vpn

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the student profile from app-private storage. Live CA / tls-auth
 * never belong in the public git tree.
 */
@Singleton
class StudentVpnProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun loadRawOrNull(): String? {
        val privateFile = File(context.filesDir, "vpn/student.ovpn")
        if (privateFile.isFile && privateFile.length() > 0L) {
            return privateFile.readText(Charsets.UTF_8)
        }
        return runCatching {
            context.assets.open("vpn/student.ovpn").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

    fun importFrom(text: String) {
        val privateFile = File(context.filesDir, "vpn/student.ovpn")
        privateFile.parentFile?.mkdirs()
        privateFile.writeText(text, Charsets.UTF_8)
    }
}
