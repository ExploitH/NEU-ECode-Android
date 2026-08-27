package com.neko.neuecode.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.neko.neuecode.MainActivity
import com.neko.neuecode.R
import com.neko.neuecode.domain.vpn.OfficialOpenVpn3Bridge
import com.neko.neuecode.domain.vpn.StudentVpnProfileSanitizer
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class StudentVpnService : VpnService() {

    @Inject lateinit var controller: StudentVpnController
    @Inject lateinit var core: OfficialOpenVpn3Bridge

    private val worker = Executors.newSingleThreadExecutor()
    private val tun = AtomicReference<ParcelFileDescriptor?>(null)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification("学生 VPN"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification("学生 VPN"))
            }
        } catch (error: Exception) {
            Timber.e(error, "startForeground failed")
            controller.onCoreEvent("FOREGROUND_FAIL", error.javaClass.simpleName, error = true, fatal = true)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_CONNECT -> worker.execute { connectInternal(null) }
            ACTION_SUBMIT -> {
                val code = intent.getStringExtra(EXTRA_CHALLENGE).orEmpty()
                worker.execute { connectInternal(code) }
            }
            ACTION_DISCONNECT -> worker.execute { disconnectInternal() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        disconnectInternal()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun connectInternal(challengeCode: String?) {
        val profile = controller.sanitizedProfileOrNull()
        val credentials = controller.credentialsOrNull()
        if (profile == null || credentials == null) {
            controller.onCoreEvent("CORE_MISSING", "profile or credentials missing", error = true, fatal = true)
            stopSelf()
            return
        }
        Timber.i(
            "starting official openvpn3 session\n%s",
            StudentVpnProfileSanitizer.redactedForLog(profile),
        )
        val response = challengeCode?.takeIf { it.isNotBlank() }
        val cookie = if (response != null) controller.pendingCookieOrNull() else null
        core.connect(
            sanitizedProfile = profile,
            username = credentials.username,
            password = credentials.password,
            challengeResponse = response,
            challengeCookie = cookie,
            listener = object : OfficialOpenVpn3Bridge.Listener {
                override fun onEvent(name: String, info: String, error: Boolean, fatal: Boolean) {
                    controller.onCoreEvent(name, info, error, fatal)
                    if (fatal && !name.equals("DYNAMIC_CHALLENGE", ignoreCase = true)) {
                        disconnectInternal()
                    }
                }

                override fun onLog(line: String) {
                    controller.onCoreLog(line)
                }

                override fun establishTun(config: OfficialOpenVpn3Bridge.TunConfig): Int {
                    return establishFromCore(config)
                }

                override fun protectSocket(fd: Int): Boolean {
                    return protect(fd)
                }
            },
        )
    }

    private fun establishFromCore(config: OfficialOpenVpn3Bridge.TunConfig): Int {
        return runCatching {
            val builder = Builder()
                .setSession(config.sessionName.ifBlank { "NEU 学生 VPN" })
                .setMtu(if (config.mtu > 0) config.mtu else 1400)
                .setBlocking(true)
            if (config.ipv4.isNotBlank()) {
                builder.addAddress(config.ipv4, config.ipv4Prefix)
            }
            if (config.ipv6.isNotBlank()) {
                builder.addAddress(config.ipv6, config.ipv6Prefix)
            }
            config.dns.forEach { dns ->
                if (dns.isNotBlank()) builder.addDnsServer(dns)
            }
            val routes = config.routes4 + config.routes6
            if (routes.isEmpty()) {
                Timber.w("openvpn3 pushed no include routes; keeping split-tunnel (no 0.0.0.0/0)")
            } else {
                routes.forEach { cidr -> addCidrRoute(builder, cidr) }
            }
            val pfd = builder.establish() ?: return -1
            tun.getAndSet(pfd)?.close()
            pfd.detachFd()
        }.getOrElse { error ->
            Timber.w(error, "tun establish failed")
            -1
        }
    }

    private fun addCidrRoute(builder: Builder, cidr: String) {
        val parts = cidr.split("/", limit = 2)
        val address = parts[0]
        val prefix = parts.getOrNull(1)?.toIntOrNull() ?: if (address.contains(':')) 128 else 32
        runCatching { builder.addRoute(address, prefix) }
            .onFailure { Timber.w(it, "skip route %s", cidr) }
    }

    private fun disconnectInternal() {
        runCatching { core.disconnect() }
        tun.getAndSet(null)?.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "学生 VPN", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("NEU e码通")
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.neko.neuecode.vpn.CONNECT"
        const val ACTION_SUBMIT = "com.neko.neuecode.vpn.SUBMIT"
        const val ACTION_DISCONNECT = "com.neko.neuecode.vpn.DISCONNECT"
        const val EXTRA_CHALLENGE = "challenge"
        private const val CHANNEL_ID = "student_vpn"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context, action: String, challenge: String? = null) {
            val intent = Intent(context, StudentVpnService::class.java).setAction(action)
            if (challenge != null) {
                intent.putExtra(EXTRA_CHALLENGE, challenge)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
