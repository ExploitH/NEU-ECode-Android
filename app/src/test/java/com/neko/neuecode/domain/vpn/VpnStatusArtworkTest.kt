package com.neko.neuecode.domain.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnStatusArtworkTest {

    @Test
    fun connected_usesConnectedArtwork() {
        assertEquals(VpnStatusArtwork.Connected, VpnStatusArtwork.forPhase(StudentVpnPhase.Connected))
    }

    @Test
    fun inProgressPhases_useConnectingArtwork() {
        val connectingPhases = listOf(
            StudentVpnPhase.Connecting,
            StudentVpnPhase.SubmittingChallenge,
            StudentVpnPhase.Disconnecting,
        )
        connectingPhases.forEach { phase ->
            assertEquals(phase.name, VpnStatusArtwork.Connecting, VpnStatusArtwork.forPhase(phase))
        }
    }

    @Test
    fun idleLikePhases_useIdleArtwork() {
        val idlePhases = listOf(
            StudentVpnPhase.Idle,
            StudentVpnPhase.NeedChallenge,
            StudentVpnPhase.Failed,
        )
        idlePhases.forEach { phase ->
            assertEquals(phase.name, VpnStatusArtwork.Idle, VpnStatusArtwork.forPhase(phase))
        }
    }

    @Test
    fun connectThenDisconnect_returnsToIdleArtwork() {
        val flow = listOf(
            StudentVpnPhase.Idle,
            StudentVpnPhase.Connecting,
            StudentVpnPhase.NeedChallenge,
            StudentVpnPhase.SubmittingChallenge,
            StudentVpnPhase.Connected,
            StudentVpnPhase.Disconnecting,
            StudentVpnPhase.Idle,
        )
        val art = flow.map(VpnStatusArtwork::forPhase)
        assertEquals(
            listOf(
                VpnStatusArtwork.Idle,
                VpnStatusArtwork.Connecting,
                VpnStatusArtwork.Idle,
                VpnStatusArtwork.Connecting,
                VpnStatusArtwork.Connected,
                VpnStatusArtwork.Connecting,
                VpnStatusArtwork.Idle,
            ),
            art,
        )
    }
}
