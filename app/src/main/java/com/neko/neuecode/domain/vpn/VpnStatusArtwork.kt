package com.neko.neuecode.domain.vpn

enum class VpnStatusArtwork {
    Idle,
    Connecting,
    Connected,
    ;

    companion object {
        /**
         * Idle / Connected logo controllers are authored at 64% of the 512 canvas.
         * Connecting reuses mountain_river_loading, which fills the canvas,
         * so it must be drawn at this scale inside the same slot.
         */
        const val CONNECTING_VISUAL_SCALE = 0.64f

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
