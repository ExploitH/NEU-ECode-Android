package com.neko.neuecode.domain.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentVpnReducerTest {

    @Test
    fun connectFlow_reachesNeedChallengeOnce() {
        var state: StudentVpnUiState = StudentVpnUiState.Idle
        state = StudentVpnReducer.reduce(state, StudentVpnEvent.ConnectRequested)
        assertEquals(StudentVpnPhase.Connecting, state.phase)

        val challenge = Crv1Challenge(
            stateId = "s1",
            username = "20240001",
            challengeText = "SMS",
            responseRequired = true,
            echo = false,
        )
        state = StudentVpnReducer.reduce(state, StudentVpnEvent.Challenge(challenge))
        assertEquals(StudentVpnPhase.NeedChallenge, state.phase)
        assertEquals("SMS", state.challenge?.challengeText)

        state = StudentVpnReducer.reduce(state, StudentVpnEvent.ChallengeSubmitted)
        assertEquals(StudentVpnPhase.SubmittingChallenge, state.phase)
        state = StudentVpnReducer.reduce(state, StudentVpnEvent.Connected(splitTunnel = true))
        assertEquals(StudentVpnPhase.Connected, state.phase)
        assertTrue(state.splitTunnel)
    }

    @Test
    fun challengeRejected_doesNotAutoRetry() {
        var state = StudentVpnUiState(phase = StudentVpnPhase.SubmittingChallenge)
        state = StudentVpnReducer.reduce(state, StudentVpnEvent.Failed("AUTH_FAILED", canRetry = false))
        assertEquals(StudentVpnPhase.Failed, state.phase)
        assertFalse(state.canAutoRetry)
        assertFalse(state.message.orEmpty().contains("CRV1"))
    }

    @Test
    fun missingOfficialCore_isFailedClosed() {
        val state = StudentVpnReducer.reduce(
            StudentVpnUiState.Idle,
            StudentVpnEvent.Failed("官方 OpenVPN 3 核心未编入本构建", canRetry = false),
        )
        assertEquals(StudentVpnPhase.Failed, state.phase)
        assertTrue(state.message.orEmpty().contains("官方 OpenVPN 3"))
    }
}
