package com.neko.neuecode.ui.screen.ecode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.repository.PersonalRepository
import com.neko.neuecode.domain.model.Balance
import com.neko.neuecode.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class ECodeUiState {
    object Loading : ECodeUiState()
    object Ready : ECodeUiState()
    data class Error(val message: String) : ECodeUiState()
}

@HiltViewModel
class ECodeViewModel @Inject constructor(
    private val personalRepository: PersonalRepository,
    private val cookieJar: PersistentCookieJar
) : ViewModel() {
    
    companion object {
        private const val ECODE_HOME_URL = "https://ecode.neu.edu.cn/ecode/#/"
        private const val ECODE_SERVICE_BRIDGE_URL = "https://pass.neu.edu.cn/tpass/login?service=https%3A%2F%2Fecode.neu.edu.cn%2Fecode%2Fapi%2Fsso%2Flogin"
        private const val BALANCE_SYNC_INTERVAL = 5 * 60 * 1000L // 5 minutes
    }
    
    private val _uiState = MutableStateFlow<ECodeUiState>(ECodeUiState.Loading)
    val uiState: StateFlow<ECodeUiState> = _uiState.asStateFlow()
    
    private val _balance = MutableStateFlow<Balance?>(null)
    val balance: StateFlow<Balance?> = _balance.asStateFlow()
    
    private val _isSyncingBalance = MutableStateFlow(false)
    val isSyncingBalance: StateFlow<Boolean> = _isSyncingBalance.asStateFlow()
    
    private val _currentUrl = MutableStateFlow(ECODE_SERVICE_BRIDGE_URL)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()
    
    init {
        // Sync cookies to WebView on initialization
        syncCookiesAndLoad()
        
        // Start periodic balance sync
        startPeriodicBalanceSync()
    }
    
    private fun syncCookiesAndLoad() {
        viewModelScope.launch {
            try {
                Timber.d("Syncing cookies to WebView...")
                
                // Get cookies for e-code bridge + final page
                val cookies = cookieJar.getCookiesForUrl(ECODE_SERVICE_BRIDGE_URL) +
                    cookieJar.getCookiesForUrl(ECODE_HOME_URL)
                Timber.d("Found ${cookies.size} cookies for e-code flow")
                
                // Cookies will be automatically synced by PersistentCookieJar
                // when they were saved from login
                
                _uiState.value = ECodeUiState.Ready
                
                // Load balance immediately
                loadBalance()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync cookies")
                _uiState.value = ECodeUiState.Error("初始化失败: ${e.message}")
            }
        }
    }
    
    fun loadBalance() {
        viewModelScope.launch {
            if (_isSyncingBalance.value) return@launch
            
            _isSyncingBalance.value = true
            Timber.d("Loading balance...")
            
            when (val result = personalRepository.getBalance()) {
                is Result.Success -> {
                    _balance.value = result.data
                    Timber.d("Balance loaded successfully")
                }
                is Result.Error -> {
                    Timber.e(result.exception, "Failed to load balance")
                    // Don't show error to user, just log it
                }
                else -> {}
            }
            
            _isSyncingBalance.value = false
        }
    }
    
    private fun startPeriodicBalanceSync() {
        viewModelScope.launch {
            while (true) {
                delay(BALANCE_SYNC_INTERVAL)
                loadBalance()
            }
        }
    }
    
    fun onPageFinished(url: String) {
        Timber.d("Page finished: $url")

        if (
            url.contains("ecode.neu.edu.cn/ecode/api/sso/login") ||
            url == "https://ecode.neu.edu.cn/ecode/api" ||
            url == "https://ecode.neu.edu.cn/ecode/api/"
        ) {
            Timber.i("ECode bridge/API landing reached, forwarding to final e-code page")
            _currentUrl.value = ECODE_HOME_URL
            return
        }

        _currentUrl.value = url
        
        // Save cookies after page loads
        viewModelScope.launch {
            try {
                // Cookies are automatically saved by PersistentCookieJar
                // when HTTP requests are made
                Timber.d("Cookies auto-saved")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save cookies")
            }
        }
    }
    
    fun reload() {
        _uiState.value = ECodeUiState.Loading
        syncCookiesAndLoad()
    }
}
