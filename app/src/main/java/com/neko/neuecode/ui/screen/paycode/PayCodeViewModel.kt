package com.neko.neuecode.ui.screen.paycode

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.repository.ECodePayCodeRepository
import com.neko.neuecode.data.repository.PersonalRepository
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import com.neko.neuecode.domain.model.Balance
import com.neko.neuecode.domain.model.Result
import com.neko.neuecode.widget.ECodeWidgetProvider
import com.neko.neuecode.widget.ECodeWidgetStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class PayCodeUiState(
    val home: PayCodeHomeState = PayCodeHomePresentation.loading(),
    val balance: Balance? = null,
    val isSyncingBalance: Boolean = false,
    val balanceError: String? = null,
    val widgetShowBalance: Boolean = true,
)

@HiltViewModel
class PayCodeViewModel @Inject constructor(
    private val personalRepository: PersonalRepository,
    private val payCodeRepository: ECodePayCodeRepository,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PayCodeUiState(widgetShowBalance = ECodeWidgetStore.load(application).showBalance),
    )
    val uiState: StateFlow<PayCodeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                home = PayCodeHomePresentation.loading(),
                isSyncingBalance = true,
                balanceError = null,
            )
            coroutineScope {
                val balanceJob = async { personalRepository.getBalance() }
                val payCodeJob = async { payCodeRepository.fetchPayCode() }
                when (val balance = balanceJob.await()) {
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
                _uiState.value = _uiState.value.copy(
                    home = PayCodeHomePresentation.from(payCode),
                )
                persistWidgetQr(payCode)
            }
        }
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
}
