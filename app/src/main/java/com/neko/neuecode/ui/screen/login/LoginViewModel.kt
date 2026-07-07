package com.neko.neuecode.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.repository.AuthRepository
import com.neko.neuecode.domain.model.Result
import com.neko.neuecode.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class AwaitingSms(
        val pending: AuthRepository.SmsVerificationRequired,
        val message: String
    ) : LoginUiState()
    data class Success(val user: User) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    @Volatile
    private var successEmitted = false

    /**
     * RSA-encrypted login with username and password
     */
    fun login(
        username: String,
        password: String,
        rememberUsername: Boolean = false,
        longTermLogin: Boolean = false
    ) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("请输入用户名和密码")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            handleLoginResult(
                authRepository.login(
                    username = username.trim(),
                    password = password,
                    rememberUsername = rememberUsername,
                    longTermLogin = longTermLogin
                )
            )
        }
    }

    fun verifySmsCode(code: String) {
        val awaiting = _uiState.value as? LoginUiState.AwaitingSms
        if (awaiting == null) {
            _uiState.value = LoginUiState.Error("当前没有待验证的短信登录")
            return
        }
        if (code.isBlank()) {
            _uiState.value = awaiting.copy(message = "请输入短信验证码")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            handleLoginResult(authRepository.verifySmsCode(awaiting.pending, code))
        }
    }

    private fun handleLoginResult(result: Result<User>) {
        when (result) {
            is Result.Success -> _uiState.value = LoginUiState.Success(result.data)
            is Result.Error -> {
                val sms = result.exception as? AuthRepository.NeedSmsVerificationException
                _uiState.value = if (sms != null) {
                    LoginUiState.AwaitingSms(
                        pending = sms.pending,
                        message = result.message ?: "需要短信验证码"
                    )
                } else {
                    LoginUiState.Error(result.message ?: result.exception.message ?: "登录失败")
                }
            }
            else -> _uiState.value = LoginUiState.Error("未知错误")
        }
    }

    fun onWebViewPageFinished(currentUrl: String, cookieUrls: List<String>) {
        if (successEmitted) return

        viewModelScope.launch {
            try {
                val result = authRepository.importWebViewLoginSession(
                    currentUrl = currentUrl,
                    cookieUrls = cookieUrls
                )
                when (result) {
                    is Result.Success -> {
                        successEmitted = true
                        Timber.i("WebView login session imported")
                        _uiState.value = LoginUiState.Success(result.data)
                    }
                    is Result.Error -> {
                        Timber.d("WebView login not ready yet: ${result.message}")
                        if (_uiState.value is LoginUiState.Loading) {
                            _uiState.value = LoginUiState.Idle
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to inspect WebView login state")
                if (_uiState.value is LoginUiState.Loading) {
                    _uiState.value = LoginUiState.Idle
                }
            }
        }
    }

    fun onWebViewError(error: String) {
        if (!successEmitted) {
            _uiState.value = LoginUiState.Error("网页加载异常：$error")
        }
    }
    
    fun resetState() {
        if (!successEmitted) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
