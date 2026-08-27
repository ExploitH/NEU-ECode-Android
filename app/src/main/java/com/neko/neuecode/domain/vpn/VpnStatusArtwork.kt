package com.neko.neuecode.domain.vpn

enum class VpnStatusArtwork {
    Idle,
    Connecting,
    Connected,
    ;

    companion object {
        fun forPhase(phase: StudentVpnPhase): VpnStatusArtwork {
            return when (phase) {
                StudentVpnPhase.Connected -> Connected
                StudentVpnPhase.Connecting,
                StudentVpnPhase.SubmittingChallenge,
                StudentVpnPhase.Disconnecting,
                -> Connecting
                StudentVpnPhase.Idle,
                StudentVpnPhase.NeedChallenge,
                StudentVpnPhase.Failed,
                -> Idle
            }
        }
    }
}
