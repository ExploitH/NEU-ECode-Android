package com.neko.neuecode.domain.vpn

enum class VpnStatusArtwork {
    Idle,
    Connecting,
    Connected,
    ;

    companion object {
        /**
         * Idle / Connected logo controllers are authored at 64% of the 512 canvas
         * (`s=[64,64]` around a 476 ellipse). Connecting reuses
         * mountain_river_loading, whose white plate is the same 476 ellipse at
         * 100% of the canvas. Draw connecting at this fraction of the shared
         * slot so the white circles match. Prefer layout size over graphicsLayer
         * — Lottie + Crossfade can ignore a graphicsLayer scale.
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
