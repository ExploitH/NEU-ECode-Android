package com.neko.neuecode.ui.screen.paycode

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.remote.jwxt.CasSecondAuthClient
import com.neko.neuecode.data.repository.AuthRepository
import com.neko.neuecode.data.repository.ECodePayCodeRepository
import com.neko.neuecode.data.repository.PersonalRepository
import com.neko.neuecode.data.repository.PersonalSessionErrors
import com.neko.neuecode.domain.ecode.EcodeModuleAvailability
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import com.neko.neuecode.domain.model.Balance
import com.neko.neuecode.domain.model.Result
import com.neko.neuecode.widget.ECodeWidgetProvider
import com.neko.neuecode.widget.ECodeWidgetStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class PayCodeUiState(
    val home: PayCodeHomeState = PayCodeHomePresentation.idle(fetchEnabled = false),
    val balance: Balance? = null,
    val isSyncingBalance: Boolean = false,
    val balanceError: String? = null,
    val widgetShowBalance: Boolean = true,
    val awaitingSms: Boolean = false,
    val fetchEnabled: Boolean = false,
    val switchHint: String = "",
    val graphicCaptcha: String = "",
    val smsCode: String = "",
    val captchaImageUrl: String? = null,
    val challengeMessage: String? = null,
    val sendingSms: Boolean = false,
    val submittingSms: Boolean = false,
    val yhtSms: Boolean = false,
    /** 「自动刷新二维码」: renew the code before its TTL runs out. */
    val autoRefresh: Boolean = true,
    /** A fetch is in flight; the current QR (if any) stays on screen meanwhile. */
    val codeRefreshing: Boolean = false,
)

@HiltViewModel
class PayCodeViewModel @Inject constructor(
    private val personalRepository: PersonalRepository,
    private val payCodeRepository: ECodePayCodeRepository,
    private val secondAuthClient: CasSecondAuthClient,
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PayCodeUiState(widgetShowBalance = ECodeWidgetStore.load(application).showBalance),
    )
    val uiState: StateFlow<PayCodeUiState> = _uiState.asStateFlow()
    private var autoFetchJob: Job? = null

    init {
        viewModelScope.launch {
            userPreferences.payCodeFetchEnabledFlow.collect { enabled ->
                val current = _uiState.value
                if (current.fetchEnabled == enabled) return@collect
                applySwitch(enabled, persist = false, userInitiated = false)
            }
        }
        viewModelScope.launch {
            val enabled = userPreferences.isPayCodeFetchEnabled()
            val locked = userPreferences.isPayCodeSmsLocked()
            val hint = userPreferences.payCodeSwitchHint()
            val autoRefresh = userPreferences.isPayCodeAutoRefreshEnabled()
            val fetchOnOpen = EcodeModuleAvailability.shouldFetchPayCode() && enabled && autoRefresh
            _uiState.value = _uiState.value.copy(
                fetchEnabled = enabled,
                awaitingSms = locked,
                switchHint = hint,
                autoRefresh = autoRefresh,
                home = if (fetchOnOpen && !locked) {
                    PayCodeHomePresentation.loading(fetchEnabled = true, switchHint = hint)
                } else {
                    PayCodeHomePresentation.idle(
                        fetchEnabled = enabled,
                        switchHint = hint,
                        awaitingSms = locked,
                        message = manualHint(enabled, autoRefresh),
                    )
                },
            )
            if (fetchOnOpen && !locked) {
                refresh(userInitiated = true)
            } else {
                stopAutoFetch()
            }
        }
    }

    fun setFetchEnabled(enabled: Boolean) {
        applySwitch(enabled, persist = true, userInitiated = true)
    }

    fun refresh() {
        refresh(userInitiated = true)
    }

    fun setAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setPayCodeAutoRefreshEnabled(enabled)
            _uiState.value = _uiState.value.copy(autoRefresh = enabled)
            if (!enabled) {
                stopAutoFetch()
                return@launch
            }
            val state = _uiState.value
            if (state.fetchEnabled && !state.awaitingSms && !state.codeRefreshing) {
                refresh(userInitiated = true)
            }
        }
    }

    /** Hint for the empty hero when the user has to tap refresh themselves. */
    private fun manualHint(fetchEnabled: Boolean, autoRefresh: Boolean): String? =
        if (fetchEnabled && !autoRefresh) MANUAL_REFRESH_HINT else null

    fun updateGraphicCaptcha(value: String) {
        _uiState.value = _uiState.value.copy(graphicCaptcha = value.filter(Char::isLetterOrDigit).take(8))
    }

    fun updateSmsCode(value: String) {
        _uiState.value = _uiState.value.copy(smsCode = value.filter(Char::isDigit).take(8))
    }

    fun refreshCaptchaImage() {
        _uiState.value = _uiState.value.copy(
            captchaImageUrl = secondAuthClient.refreshCaptchaUrl(),
            challengeMessage = null,
        )
    }

    fun sendSmsCode() {
        val yht = personalRepository.pendingYhtSms()
        if (yht != null) {
            if (_uiState.value.sendingSms) return
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(sendingSms = true, challengeMessage = null)
                withContext(Dispatchers.IO) { runCatching { authRepository.sendSmsCode(yht) } }
                _uiState.value = _uiState.value.copy(
                    sendingSms = false,
                    challengeMessage = "验证码已发送，请查收后填写。不要反复点击发送。",
                    yhtSms = true,
                )
            }
            return
        }
        val graphic = _uiState.value.graphicCaptcha.trim()
        if (graphic.isBlank()) {
            _uiState.value = _uiState.value.copy(challengeMessage = "请先输入图形验证码")
            return
        }
        if (_uiState.value.sendingSms) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sendingSms = true, challengeMessage = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    secondAuthClient.sendSmsCode(
                        pageUrl = "https://pass.neu.edu.cn/tpass/login?service=https%3A%2F%2Fecode.neu.edu.cn%2Fecode%2Fapi%2Fsso%2Flogin",
                        graphicCaptcha = graphic,
                    )
                }.getOrElse { error ->
                    com.neko.neuecode.data.remote.jwxt.CasSecondAuthSendResult(
                        ok = false,
                        message = error.message ?: "验证码发送失败",
                    )
                }
            }
            _uiState.value = _uiState.value.copy(
                sendingSms = false,
                challengeMessage = result.message,
                captchaImageUrl = if (result.ok) _uiState.value.captchaImageUrl else secondAuthClient.refreshCaptchaUrl(),
            )
        }
    }

    fun submitSmsCode() {
        val sms = _uiState.value.smsCode.trim()
        if (sms.length < 4) {
            _uiState.value = _uiState.value.copy(challengeMessage = "请输入短信验证码")
            return
        }
        val yht = personalRepository.pendingYhtSms()
        if (yht != null) {
            if (_uiState.value.submittingSms) return
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(submittingSms = true, challengeMessage = null)
                val result = withContext(Dispatchers.IO) { authRepository.verifySmsCode(yht, sms) }
                when (result) {
                    is Result.Success -> {
                        personalRepository.clearYhtSms()
                        userPreferences.setPayCodeSmsLock(false)
                        _uiState.value = _uiState.value.copy(
                            submittingSms = false,
                            awaitingSms = false,
                            yhtSms = false,
                            challengeMessage = "验证成功，正在刷新…",
                            smsCode = "",
                        )
                        refresh(userInitiated = true)
                    }
                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            submittingSms = false,
                            challengeMessage = result.message ?: "短信验证失败",
                            yhtSms = true,
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(submittingSms = false)
                    }
                }
            }
            return
        }
        val challenge = payCodeRepository.pendingSmsChallenge()
        if (challenge == null || !challenge.isPresent) {
            _uiState.value = _uiState.value.copy(challengeMessage = "验证会话已失效，请重新打开开关取码")
            return
        }
        if (_uiState.value.submittingSms) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(submittingSms = true, challengeMessage = null)
            val result = withContext(Dispatchers.IO) {
                runCatching { secondAuthClient.submitSmsCode(challenge, sms) }.getOrElse { error ->
                    com.neko.neuecode.data.remote.jwxt.CasSecondAuthSubmitResult(
                        ok = false,
                        message = error.message ?: "验证失败",
                        finalUrl = "",
                    )
                }
            }
            if (result.ok) {
                payCodeRepository.clearSmsChallenge()
                userPreferences.setPayCodeSmsLock(false)
                _uiState.value = _uiState.value.copy(
                    submittingSms = false,
                    awaitingSms = false,
                    challengeMessage = "验证成功，正在取码…",
                    smsCode = "",
                    graphicCaptcha = "",
                )
                refresh(userInitiated = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    submittingSms = false,
                    challengeMessage = result.message,
                )
            }
        }
    }

    private fun applySwitch(enabled: Boolean, persist: Boolean, userInitiated: Boolean) {
        viewModelScope.launch {
            if (persist) {
                userPreferences.setPayCodeFetchEnabled(enabled)
            }
            if (!enabled) {
                stopAutoFetch()
                if (!userInitiated && _uiState.value.awaitingSms) {
                    // keep SMS lock hint
                } else if (!_uiState.value.awaitingSms) {
                    userPreferences.setPayCodeSmsLock(false)
                }
                _uiState.value = _uiState.value.copy(
                    fetchEnabled = false,
                    home = PayCodeHomePresentation.idle(
                        fetchEnabled = false,
                        switchHint = _uiState.value.switchHint,
                        awaitingSms = _uiState.value.awaitingSms,
                        maskedPhone = payCodeRepository.pendingSmsChallenge()?.maskedPhone,
                    ),
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(fetchEnabled = true)
            if (!_uiState.value.awaitingSms && !_uiState.value.autoRefresh) {
                // Manual mode: turning e码通 on does not fetch until the user taps refresh.
                if (_uiState.value.home.status != PayCodeHomeStatus.Ready) {
                    _uiState.value = _uiState.value.copy(
                        home = PayCodeHomePresentation.idle(
                            fetchEnabled = true,
                            switchHint = _uiState.value.switchHint,
                            message = MANUAL_REFRESH_HINT,
                        ),
                    )
                }
                return@launch
            }
            if (_uiState.value.awaitingSms) {
                _uiState.value = _uiState.value.copy(
                    home = PayCodeHomePresentation.from(
                        PayCodeParseResult.Failure(
                            PayCodeFailure.NeedSms,
                            _uiState.value.home.syncHint ?: PayCodeFetchGate.MANUAL_SMS_HINT,
                        ),
                        fetchEnabled = true,
                        switchHint = _uiState.value.switchHint,
                        maskedPhone = payCodeRepository.pendingSmsChallenge()?.maskedPhone,
                    ),
                    captchaImageUrl = _uiState.value.captchaImageUrl ?: secondAuthClient.refreshCaptchaUrl(),
                )
                return@launch
            }
            refresh(userInitiated = true)
        }
    }

    private fun refresh(userInitiated: Boolean) {
        if (!EcodeModuleAvailability.shouldFetchPayCode()) {
            stopAutoFetch()
            return
        }
        val fetchEnabled = _uiState.value.fetchEnabled
        if (!PayCodeRefreshPolicy.canRefreshPayCode(
                awaitingSms = _uiState.value.awaitingSms && !userInitiated,
                isRefreshing = _uiState.value.codeRefreshing,
                fetchEnabled = fetchEnabled,
            )
        ) {
            return
        }
        val autoRefresh = _uiState.value.autoRefresh
        if (!userInitiated && !PayCodeRefreshPolicy.shouldContinueAutoFetch(
                awaitingSms = _uiState.value.awaitingSms,
                fetchEnabled = fetchEnabled,
                autoRefresh = autoRefresh,
            )
        ) {
            stopAutoFetch()
            return
        }
        // Mark in flight synchronously so a second tap cannot start a parallel fetch.
        _uiState.value = _uiState.value.copy(codeRefreshing = true)
        viewModelScope.launch {
            // Keep the current QR visible while the next one loads; it is swapped in place.
            val hasQr = _uiState.value.home.status == PayCodeHomeStatus.Ready && _uiState.value.home.showNativeQr
            _uiState.value = _uiState.value.copy(
                home = if (hasQr) {
                    _uiState.value.home
                } else {
                    PayCodeHomePresentation.loading(
                        fetchEnabled = fetchEnabled,
                        switchHint = _uiState.value.switchHint,
                    )
                },
                isSyncingBalance = true,
                balanceError = null,
            )
            try {
            coroutineScope {
                val balanceJob = async { personalRepository.getBalance() }
                val payCodeJob = async { payCodeRepository.fetchPayCode() }
                val balance = balanceJob.await()
                when (balance) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(
                            balance = balance.data,
                            isSyncingBalance = false,
                            balanceError = null,
                        )
                    }
                    is Result.Error -> {
                        Timber.w(balance.exception, "PayCode balance refresh failed")
                        _uiState.value = _uiState.value.copy(
                            isSyncingBalance = false,
                            balanceError = balance.message ?: "余额暂时不可用",
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(isSyncingBalance = false)
                    }
                }
                val payCode = payCodeJob.await()
                val yhtSms = PersonalSessionErrors.isNeedSms((balance as? Result.Error)?.exception) ||
                    PersonalSessionErrors.isNeedSms((balance as? Result.Error)?.message) ||
                    personalRepository.pendingYhtSms() != null
                val casSms = payCode is PayCodeParseResult.Failure &&
                    payCode.reason == PayCodeFailure.NeedSms
                val awaitingSms = casSms || yhtSms
                val snapshot = if (awaitingSms) {
                    PayCodeFetchGate.afterNeedSms(
                        userInitiated = userInitiated,
                        currentSwitchOn = fetchEnabled,
                        currentAutoRefresh = _uiState.value.autoRefresh,
                    )
                } else if (payCode is PayCodeParseResult.Success) {
                    PayCodeFetchGate.afterSuccess(currentAutoRefresh = _uiState.value.autoRefresh)
                } else {
                    PayCodeSwitchSnapshot(
                        userSwitchOn = fetchEnabled,
                        lockedBySms = false,
                        switchHint = _uiState.value.switchHint,
                        autoRefreshOn = _uiState.value.autoRefresh,
                    )
                }
                if (snapshot.userSwitchOn != fetchEnabled) {
                    userPreferences.setPayCodeFetchEnabled(snapshot.userSwitchOn)
                }
                if (snapshot.autoRefreshOn != _uiState.value.autoRefresh) {
                    userPreferences.setPayCodeAutoRefreshEnabled(snapshot.autoRefreshOn)
                }
                userPreferences.setPayCodeSmsLock(snapshot.lockedBySms, snapshot.switchHint)
                if (!casSms && payCode is PayCodeParseResult.Success) {
                    payCodeRepository.clearSmsChallenge()
                }
                val presented = if (yhtSms && !casSms) {
                    PayCodeHomePresentation.from(
                        PayCodeParseResult.Failure(
                            PayCodeFailure.NeedSms,
                            (balance as? Result.Error)?.message ?: PayCodeFetchGate.MANUAL_SMS_HINT,
                        ),
                        fetchEnabled = snapshot.userSwitchOn,
                        switchHint = snapshot.switchHint,
                    )
                } else {
                    PayCodeHomePresentation.from(
                        payCode,
                        fetchEnabled = snapshot.userSwitchOn,
                        switchHint = snapshot.switchHint,
                        maskedPhone = payCodeRepository.pendingSmsChallenge()?.maskedPhone,
                    )
                }
                _uiState.value = _uiState.value.copy(
                    home = presented,
                    awaitingSms = snapshot.lockedBySms,
                    fetchEnabled = snapshot.userSwitchOn,
                    switchHint = snapshot.switchHint,
                    autoRefresh = snapshot.autoRefreshOn,
                    yhtSms = yhtSms,
                    captchaImageUrl = if (snapshot.lockedBySms && casSms) {
                        _uiState.value.captchaImageUrl ?: secondAuthClient.refreshCaptchaUrl()
                    } else {
                        null
                    },
                    challengeMessage = if (snapshot.lockedBySms) snapshot.switchHint else null,
                    balanceError = if (yhtSms) null else _uiState.value.balanceError,
                )
                persistWidgetQr(payCode)
                scheduleAutoFetch(payCode, snapshot.lockedBySms, snapshot.userSwitchOn, snapshot.autoRefreshOn)
            }
            } finally {
                _uiState.value = _uiState.value.copy(codeRefreshing = false)
            }
        }
    }

    private fun scheduleAutoFetch(
        result: PayCodeParseResult,
        awaitingSms: Boolean,
        fetchEnabled: Boolean,
        autoRefresh: Boolean,
    ) {
        autoFetchJob?.cancel()
        autoFetchJob = null
        if (!PayCodeRefreshPolicy.shouldContinueAutoFetch(awaitingSms, fetchEnabled, autoRefresh)) {
            return
        }
        val delayMs = PayCodeRefreshPolicy.nextAutoFetchDelayMs(
            success = result is PayCodeParseResult.Success,
            ttlSeconds = (result as? PayCodeParseResult.Success)?.code?.ttlSeconds,
            awaitingSms = awaitingSms,
            fetchEnabled = fetchEnabled,
            autoRefresh = autoRefresh,
        ) ?: return
        autoFetchJob = viewModelScope.launch {
            delay(delayMs)
            val state = _uiState.value
            if (!PayCodeRefreshPolicy.shouldContinueAutoFetch(state.awaitingSms, state.fetchEnabled, state.autoRefresh)) {
                return@launch
            }
            refresh(userInitiated = false)
        }
    }

    private fun stopAutoFetch() {
        autoFetchJob?.cancel()
        autoFetchJob = null
    }

    fun setWidgetShowBalance(show: Boolean) {
        ECodeWidgetStore.saveShowBalance(application, show)
        _uiState.value = _uiState.value.copy(widgetShowBalance = show)
        ECodeWidgetProvider.notifyViews(application)
    }

    private suspend fun persistWidgetQr(result: PayCodeParseResult) {
        withContext(Dispatchers.IO) {
            when (result) {
                is PayCodeParseResult.Success -> {
                    val bitmap = PayCodeQrEncoder.encodeBitmap(result.code.payload, sizePx = 512)
                    if (bitmap != null) {
                        ECodeWidgetStore.saveQrBitmap(application, bitmap)
                        ECodeWidgetStore.saveStatus(
                            application,
                            com.neko.neuecode.widget.ECodeWidgetPresentation.qrStatus(true, result.code.ttlSeconds),
                        )
                    }
                }
                is PayCodeParseResult.Failure -> Unit
            }
            ECodeWidgetProvider.notifyViews(application)
        }
    }

    companion object {
        const val MANUAL_REFRESH_HINT = "自动刷新已关闭，点击刷新获取付款码"
    }
}
