package com.neko.neuecode.data.vpn

import com.neko.neuecode.domain.vpn.OfficialOpenVpn3Bridge
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeOpenVpn3Bridge @Inject constructor() : OfficialOpenVpn3Bridge {
    private val listenerRef = AtomicReference<OfficialOpenVpn3Bridge.Listener?>(null)

    override val available: Boolean = LIBRARY_LOADED
    override val engineName: String =
        if (LIBRARY_LOADED) "OpenVPN/openvpn3 (MPL-2.0, bundled)"
        else "OpenVPN/openvpn3 (MPL-2.0, not bundled in this APK)"

    override fun connect(
        sanitizedProfile: String,
        username: String,
        password: String,
        challengeResponse: String?,
        listener: OfficialOpenVpn3Bridge.Listener,
    ) {
        if (!LIBRARY_LOADED) {
            listener.onEvent("CORE_MISSING", "官方 OpenVPN 3 核心未编入本构建", error = true, fatal = true)
            return
        }
        listenerRef.set(listener)
        nativeSetListener(this)
        val message = nativeConnect(
            sanitizedProfile,
            username,
            password,
            challengeResponse.orEmpty(),
        )
        nativeSetListener(null)
        listenerRef.set(null)
        if (message.isNotEmpty()) {
            listener.onEvent("CONNECT_ERROR", message, error = true, fatal = true)
        }
    }

    override fun disconnect() {
        if (LIBRARY_LOADED) {
            nativeStop()
        }
    }

    @Suppress("unused")
    fun onNativeEvent(name: String, info: String, error: Boolean, fatal: Boolean) {
        listenerRef.get()?.onEvent(name, info, error, fatal)
    }

    @Suppress("unused")
    fun onNativeLog(line: String) {
        listenerRef.get()?.onLog(line)
    }

    @Suppress("unused")
    fun onEstablishTun(
        sessionName: String,
        ipv4: String,
        ipv4Prefix: Int,
        ipv4Gateway: String,
        ipv6Prefix: Int,
        ipv6: String,
        dns: Array<String>,
        routes4: Array<String>,
        routes6: Array<String>,
        mtu: Int,
    ): Int {
        return listenerRef.get()?.establishTun(
            OfficialOpenVpn3Bridge.TunConfig(
                sessionName = sessionName,
                ipv4 = ipv4,
                ipv4Prefix = ipv4Prefix,
                ipv4Gateway = ipv4Gateway,
                ipv6 = ipv6,
                ipv6Prefix = ipv6Prefix,
                dns = dns.toList(),
                routes4 = routes4.toList(),
                routes6 = routes6.toList(),
                mtu = mtu,
            ),
        ) ?: -1
    }

    @Suppress("unused")
    fun onProtectSocket(fd: Int): Boolean {
        return listenerRef.get()?.protectSocket(fd) == true
    }

    private external fun nativeAvailable(): Boolean
    private external fun nativeSetListener(bridge: NativeOpenVpn3Bridge?)
    private external fun nativeConnect(
        profile: String,
        username: String,
        password: String,
        challenge: String,
    ): String
    private external fun nativeStop()

    companion object {
        private val LIBRARY_LOADED: Boolean = runCatching {
            System.loadLibrary("ovpncli")
            true
        }.onFailure { error ->
            Timber.w(error, "official openvpn3 native library missing")
        }.getOrDefault(false)
    }
}
