package com.neko.neuecode.data.remote.campus

import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Campus reachability gate used before JWXT schedule sync.
 *
 * ICMP to ipgw.neu.edu.cn is what the product asked for, but Hermes/AVD
 * inherit the builder host's campus route, so that ping can succeed even
 * without the student VPN. JWXT TCP :443 is the actual 教务 path and fails
 * quickly off-campus / without tun0.
 */
@Singleton
class CampusIntranetProbe @Inject constructor() {

    internal var ping: (host: String) -> PingOutcome = Companion::systemPing
    internal var tcpConnect: (host: String, port: Int, timeoutMs: Int) -> Boolean = Companion::systemTcp

    constructor(
        ping: (host: String) -> PingOutcome,
        tcpConnect: (host: String, port: Int, timeoutMs: Int) -> Boolean = Companion::systemTcp,
    ) : this() {
        this.ping = ping
        this.tcpConnect = tcpConnect
    }

    data class PingOutcome(
        val exitCode: Int,
        val timedOut: Boolean,
    )

    data class Result(
        val host: String,
        val reachable: Boolean,
        val shouldAbortScheduleSync: Boolean,
        val detail: String,
    )

    fun probe(
        pingHost: String = PING_HOST,
        jwxtHost: String = JWXT_HOST,
        jwxtPort: Int = JWXT_PORT,
    ): Result {
        val outcome = try {
            ping(pingHost)
        } catch (e: Exception) {
            Timber.w(e, "campus ping failed")
            PingOutcome(exitCode = -1, timedOut = true)
        }
        val pingOk = !outcome.timedOut && outcome.exitCode == 0
        if (!pingOk) {
            val detail = if (outcome.timedOut) "ping $pingHost 超时" else "ping $pingHost 失败 (exit=${outcome.exitCode})"
            Timber.i("campus probe abort: %s", detail)
            return Result(
                host = pingHost,
                reachable = false,
                shouldAbortScheduleSync = true,
                detail = detail,
            )
        }
        val tcpOk = try {
            tcpConnect(jwxtHost, jwxtPort, TCP_TIMEOUT_MS)
        } catch (e: Exception) {
            Timber.w(e, "jwxt tcp probe failed")
            false
        }
        if (!tcpOk) {
            val detail = "无法在 ${TCP_TIMEOUT_MS / 1000}s 内连接 $jwxtHost:$jwxtPort"
            Timber.i("campus probe abort: %s", detail)
            return Result(
                host = jwxtHost,
                reachable = false,
                shouldAbortScheduleSync = true,
                detail = detail,
            )
        }
        return Result(
            host = pingHost,
            reachable = true,
            shouldAbortScheduleSync = false,
            detail = "ping $pingHost 成功，且 $jwxtHost:$jwxtPort 可达",
        )
    }

    companion object {
        const val PING_HOST = "ipgw.neu.edu.cn"
        const val JWXT_HOST = "jwxt.neu.edu.cn"
        const val JWXT_PORT = 443
        const val TIMEOUT_SECONDS = 3
        const val TCP_TIMEOUT_MS = 3_000

        fun systemPing(host: String): PingOutcome {
            return try {
                val process = ProcessBuilder("ping", "-c", "1", "-W", TIMEOUT_SECONDS.toString(), host)
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return PingOutcome(exitCode = -1, timedOut = true)
                }
                PingOutcome(exitCode = process.exitValue(), timedOut = false)
            } catch (e: Exception) {
                Timber.w(e, "system ping unavailable")
                PingOutcome(exitCode = -1, timedOut = true)
            }
        }

        fun systemTcp(host: String, port: Int, timeoutMs: Int): Boolean {
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    socket.isConnected
                }
            } catch (e: Exception) {
                Timber.i("tcp %s:%s failed: %s", host, port, e.javaClass.simpleName)
                false
            }
        }
    }
}
