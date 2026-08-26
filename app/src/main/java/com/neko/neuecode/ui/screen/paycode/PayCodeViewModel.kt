package com.neko.neuecode.ui.screen.paycode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.repository.ECodePayCodeRepository
import com.neko.neuecode.data.repository.PersonalRepository
import com.neko.neuecode.domain.model.Balance
import com.neko.neuecode.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PayCodeUiState(
    val home: PayCodeHomeState = PayCodeHomePresentation.loading(),
    val balance: Balance? = null,
    val isSyncingBalance: Boolean = false,
    val balanceError: String? = null,
)

@HiltViewModel
class PayCodeViewModel @Inject constructor(
    private val personalRepository: PersonalRepository,
    private val payCodeRepository: ECodePayCodeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayCodeUiState())
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
                _uiState.value = _uiState.value.copy(
                    home = PayCodeHomePresentation.from(payCodeJob.await()),
                )
            }
        }
    }
}
