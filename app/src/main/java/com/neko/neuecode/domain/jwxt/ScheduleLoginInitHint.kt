package com.neko.neuecode.domain.jwxt

object ScheduleLoginInitHint {
    const val LOGIN_STEP = 2
    const val DELAY_MS = 4_000L
    const val TEXT = "初次登入教务系统需要初始化资源，请耐心等待！"

    data class State(
        val watchingLogin: Boolean = false,
        val latched: Boolean = false,
    )

    fun onProgress(state: State, step: Int): State {
        if (state.latched) return state.copy(watchingLogin = step == LOGIN_STEP)
        return if (step == LOGIN_STEP) {
            state.copy(watchingLogin = true)
        } else {
            state.copy(watchingLogin = false)
        }
    }

    fun onLoginWaitElapsed(state: State, stillOnLoginStep: Boolean): State {
        if (state.latched) return state
        if (!state.watchingLogin || !stillOnLoginStep) return state
        return state.copy(latched = true)
    }

    fun onFinished(): State = State()
}
