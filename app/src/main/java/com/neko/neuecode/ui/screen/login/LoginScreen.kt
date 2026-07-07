package com.neko.neuecode.ui.screen.login

import android.webkit.CookieManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neko.neuecode.ui.components.NeuWebView

// Use personal.neu.edu.cn as entry point - it auto-redirects to CAS login
// and provides the cleanest cookie flow for subsequent API calls
private const val PERSONAL_LOGIN_URL = "https://personal.neu.edu.cn/portal/"
private const val ECODE_URL = "https://ecode.neu.edu.cn/ecode/#/"

private val LOGIN_COOKIE_URLS = listOf(
    "https://ecode.neu.edu.cn/",
    "https://ecode.neu.edu.cn/ecode/",
    "https://personal.neu.edu.cn/",
    "https://personal.neu.edu.cn/portal/",
    "https://pass.neu.edu.cn/",
    "https://pass.neu.edu.cn/cas/"
)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var webViewKey by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(PERSONAL_LOGIN_URL) }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "使用校方页面登录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { webViewKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "智慧东大登录接口使用加密协议（RSA-1024 加密的 content + authorization-str）。本 app 采用 WebView/CAS 方案：通过统一身份认证登录后自动保存 Cookie（SESS_ID、CK_LC），后续 API 调用和 e码通页面共用登录态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
                if (currentUrl.contains("pass.neu.edu.cn") || currentUrl.contains("ecode.neu.edu.cn")) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "当前：${currentUrl.take(90)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                    )
                }
            }
        }

        if (uiState is LoginUiState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (uiState is LoginUiState.Error) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = (uiState as LoginUiState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        key(webViewKey) {
            NeuWebView(
                url = PERSONAL_LOGIN_URL,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onPageStarted = { url -> currentUrl = url },
                onPageFinished = { url ->
                    currentUrl = url
                    viewModel.onWebViewPageFinished(url, LOGIN_COOKIE_URLS)
                },
                onError = { error -> viewModel.onWebViewError(error) }
            )
        }
    }
}

internal fun hasLikelyNeuLoginCookie(): Boolean {
    val manager = CookieManager.getInstance()
    return LOGIN_COOKIE_URLS.any { url ->
        val cookie = manager.getCookie(url).orEmpty()
        cookie.contains("SESSION=") ||
                cookie.contains("SESS_ID=") ||
                cookie.contains("CK_LC=") ||
                cookie.contains("CASTGC=")
    }
}
