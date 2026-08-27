package com.neko.neuecode.domain.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLoginInitHintTest {

    @Test
    fun latch_onlyWhenLoginStepStaysLongEnough() {
        var state = ScheduleLoginInitHint.State()
        state = ScheduleLoginInitHint.onProgress(state, 1)
        assertFalse(state.watchingLogin)
        assertFalse(state.latched)

        state = ScheduleLoginInitHint.onProgress(state, 2)
        assertTrue(state.watchingLogin)
        state = ScheduleLoginInitHint.onLoginWaitElapsed(state, stillOnLoginStep = true)
        assertTrue(state.latched)
    }

    @Test
    fun noLatch_whenLoginStepFinishesBeforeDelay() {
        var state = ScheduleLoginInitHint.onProgress(ScheduleLoginInitHint.State(), 2)
        state = ScheduleLoginInitHint.onProgress(state, 3)
        state = ScheduleLoginInitHint.onLoginWaitElapsed(state, stillOnLoginStep = false)
        assertFalse(state.latched)
    }

    @Test
    fun latchedHint_survivesLaterStepsUntilFinished() {
        var state = ScheduleLoginInitHint.onProgress(ScheduleLoginInitHint.State(), 2)
        state = ScheduleLoginInitHint.onLoginWaitElapsed(state, stillOnLoginStep = true)
        state = ScheduleLoginInitHint.onProgress(state, 6)
        assertTrue(state.latched)
        state = ScheduleLoginInitHint.onFinished()
        assertFalse(state.latched)
    }
}
