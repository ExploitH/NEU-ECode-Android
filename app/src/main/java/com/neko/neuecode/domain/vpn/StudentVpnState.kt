package com.neko.neuecode.domain.vpn

enum class StudentVpnPhase {
    Idle,
    Connecting,
    NeedChallenge,
    SubmittingChallenge,
    Connected,
    Disconnecting,
    Failed,
}

data class StudentVpnUiState(
    val phase: StudentVpnPhase = StudentVpnPhase.Idle,
    val username: String? = null,
    val challenge: Crv1Challenge? = null,
    val message: String? = null,
    val splitTunnel: Boolean = false,
    val canAutoRetry: Boolean = false,
    val coreReady: Boolean = false,
) {
    companion object {
        val Idle = StudentVpnUiState()
    }
}

sealed class StudentVpnEvent {
    data object ConnectRequested : StudentVpnEvent()
    data class Challenge(val challenge: Crv1Challenge) : StudentVpnEvent()
    data object ChallengeSubmitted : StudentVpnEvent()
    data class Connected(val splitTunnel: Boolean) : StudentVpnEvent()
    data object DisconnectRequested : StudentVpnEvent()
    data object Disconnected : StudentVpnEvent()
    data class Failed(val message: String, val canRetry: Boolean) : StudentVpnEvent()
    data class CoreAvailability(val ready: Boolean) : StudentVpnEvent()
}

object StudentVpnReducer {
    fun reduce(state: StudentVpnUiState, event: StudentVpnEvent): StudentVpnUiState {
        return when (event) {
            StudentVpnEvent.ConnectRequested -> state.copy(
                phase = StudentVpnPhase.Connecting,
                message = "正在连接学生 VPN…",
                canAutoRetry = false,
                challenge = null,
            )
            is StudentVpnEvent.Challenge -> state.copy(
                phase = StudentVpnPhase.NeedChallenge,
                challenge = event.challenge,
                message = event.challenge.challengeText.ifBlank { "请输入短信验证码" },
                canAutoRetry = false,
            )
            StudentVpnEvent.ChallengeSubmitted -> state.copy(
                phase = StudentVpnPhase.SubmittingChallenge,
                message = "正在提交验证码…",
                canAutoRetry = false,
            )
            is StudentVpnEvent.Connected -> state.copy(
                phase = StudentVpnPhase.Connected,
                challenge = null,
                splitTunnel = event.splitTunnel,
                message = if (event.splitTunnel) "已连接（分流，公网不走隧道）" else "已连接",
                canAutoRetry = false,
            )
            StudentVpnEvent.DisconnectRequested -> state.copy(
                phase = StudentVpnPhase.Disconnecting,
                message = "正在断开…",
            )
            StudentVpnEvent.Disconnected -> {
                if (state.phase == StudentVpnPhase.NeedChallenge ||
                    state.phase == StudentVpnPhase.SubmittingChallenge
                ) {
                    state
                } else {
                    state.copy(
                        phase = StudentVpnPhase.Idle,
                        challenge = null,
                        splitTunnel = false,
                        message = "已断开",
                        canAutoRetry = false,
                    )
                }
            }
            is StudentVpnEvent.Failed -> state.copy(
                phase = StudentVpnPhase.Failed,
                message = event.message,
                canAutoRetry = event.canRetry,
            )
            is StudentVpnEvent.CoreAvailability -> state.copy(coreReady = event.ready)
        }
    }
}
