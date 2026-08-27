package com.neko.neuecode.data.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.neko.neuecode.data.local.secure.SecureCredentialStore
import com.neko.neuecode.domain.vpn.Crv1Challenge
import com.neko.neuecode.domain.vpn.OfficialOpenVpn3Bridge
import com.neko.neuecode.domain.vpn.StudentVpnEvent
import com.neko.neuecode.domain.vpn.StudentVpnPhase
import com.neko.neuecode.domain.vpn.StudentVpnReducer
import com.neko.neuecode.domain.vpn.StudentVpnUiState
import com.neko.neuecode.domain.vpn.StudentVpnProfileSanitizer
import com.neko.neuecode.domain.vpn.StudentVpnUserMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentVpnController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialStore: SecureCredentialStore,
    private val profileStore: StudentVpnProfileStore,
    private val core: OfficialOpenVpn3Bridge,
) {
    private val _state = MutableStateFlow(
        StudentVpnUiState(coreReady = core.available),
    )
    val state: StateFlow<StudentVpnUiState> = _state.asStateFlow()

    @Volatile
    private var pendingChallenge: Crv1Challenge? = null

    fun prepareIntent(): Intent? = VpnService.prepare(context)

    fun connect() {
        val credentials = credentialStore.load()
        if (credentials == null) {
            publish(StudentVpnEvent.Failed("请先开启长效登录，内网连接使用同一套学号/密码", canRetry = false))
            return
        }
        val raw = profileStore.loadRawOrNull()
        if (raw.isNullOrBlank()) {
            publish(StudentVpnEvent.Failed("未找到学生 VPN 配置（不会从公开仓库读取证书）", canRetry = false))
            return
        }
        if (!core.available) {
            publish(StudentVpnEvent.Failed("官方 OpenVPN 3 核心未编入本构建", canRetry = false))
            return
        }
        _state.value = _state.value.copy(username = credentials.username)
        publish(StudentVpnEvent.ConnectRequested)
        StudentVpnService.start(context, StudentVpnService.ACTION_CONNECT)
    }

    fun submitChallenge(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || pendingChallenge == null) {
            publish(StudentVpnEvent.Failed("没有等待中的验证码挑战", canRetry = false))
            return
        }
        publish(StudentVpnEvent.ChallengeSubmitted)
        StudentVpnService.start(context, StudentVpnService.ACTION_SUBMIT, trimmed)
    }

    fun disconnect() {
        publish(StudentVpnEvent.DisconnectRequested)
        StudentVpnService.start(context, StudentVpnService.ACTION_DISCONNECT)
    }

    internal fun sanitizedProfileOrNull(): String? {
        val raw = profileStore.loadRawOrNull() ?: return null
        return StudentVpnProfileSanitizer.sanitize(raw)
    }

    internal fun credentialsOrNull(): SecureCredentialStore.Credentials? = credentialStore.load()

    internal fun onCoreEvent(name: String, info: String, error: Boolean, fatal: Boolean) {
        val sanitizedInfo = info
            .replace(Regex("CRV1:[^\\s]+"), "CRV1:[REDACTED]")
            .replace(Regex("(?i)password[=:].*"), "password=[REDACTED]")
        Timber.i("openvpn3 event %s error=%s fatal=%s info=%s", name, error, fatal, sanitizedInfo)
        when {
            name.equals("CONNECTED", ignoreCase = true) -> {
                pendingChallenge = null
                publish(StudentVpnEvent.Connected(splitTunnel = true))
            }
            name.equals("DYNAMIC_CHALLENGE", ignoreCase = true) ||
                name.contains("AUTH_FAILED", ignoreCase = true) ||
                info.contains("CRV1:") -> {
                val cookie = extractCrv1(info) ?: info.trim().takeIf { it.startsWith("CRV1:") }
                val challenge = cookie?.let(Crv1Challenge::parse)
                    ?: Crv1Challenge.parse(info.trim())
                if (challenge != null) {
                    pendingChallenge = challenge
                    publish(StudentVpnEvent.Challenge(challenge))
                } else if (name.equals("DYNAMIC_CHALLENGE", ignoreCase = true)) {
                    pendingChallenge = Crv1Challenge(
                        stateId = "unknown",
                        username = _state.value.username.orEmpty(),
                        challengeText = "请输入短信验证码",
                        responseRequired = true,
                        echo = false,
                    )
                    publish(StudentVpnEvent.Challenge(pendingChallenge!!))
                } else {
                    pendingChallenge = null
                    publish(StudentVpnEvent.Failed("认证失败，请重新获取短信验证码后再试一次", canRetry = false))
                }
            }
            name.equals("DISCONNECTED", ignoreCase = true) -> {
                if (pendingChallenge == null &&
                    _state.value.phase != StudentVpnPhase.NeedChallenge
                ) {
                    publish(StudentVpnEvent.Disconnected)
                }
            }
            name.equals("CONNECT_ERROR", ignoreCase = true) && pendingChallenge != null -> {
                // nativeConnect returns after a dynamic challenge; keep waiting for SMS.
            }
            fatal || error -> {
                if (pendingChallenge == null) {
                    publish(StudentVpnEvent.Failed(userSafeMessage(name, sanitizedInfo), canRetry = false))
                }
            }
        }
    }

    internal fun onCoreLog(line: String) {
        val redacted = line
            .replace(Regex("CRV1:[^\\s]+"), "CRV1:[REDACTED]")
            .replace(Regex("(?i)password[=:]\\S+"), "password=[REDACTED]")
        Timber.d("openvpn3 %s", redacted)
    }

    internal fun consumeChallengePassword(response: String): String? {
        return pendingChallenge?.buildPassword(response)
    }

    internal fun pendingCookieOrNull(): String? {
        return pendingChallenge?.rawCookie?.takeIf { it.startsWith("CRV1:") }
    }

    private fun extractCrv1(info: String): String? {
        val start = info.indexOf("CRV1:")
        if (start < 0) return null
        return info.substring(start).trim()
    }

    private fun userSafeMessage(name: String, info: String): String {
        return StudentVpnUserMessage.from(name, info)
    }

    private fun publish(event: StudentVpnEvent) {
        _state.value = StudentVpnReducer.reduce(_state.value, event)
    }
}
