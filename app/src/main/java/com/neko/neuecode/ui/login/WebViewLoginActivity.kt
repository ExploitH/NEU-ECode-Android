package com.neko.neuecode.ui.login

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.ui.theme.NeuECodeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import javax.inject.Inject

/**
 * WebView-based CAS login activity
 * 
 * Strategy: Let WebView handle the full CAS authentication flow,
 * then intercept the success redirect to extract cookies and ticket.
 * 
 * Flow:
 * 1. Load personal.neu.edu.cn (auto-redirects to CAS login)
 * 2. User completes CAS authentication in WebView
 * 3. CAS redirects back with ticket=ST-xxx
 * 4. Intercept the redirect and extract cookies (SESS_ID, CK_LC, CK_VL)
 * 5. Sync cookies to OkHttp CookieJar
 * 6. Save credentials to DataStore
 * 7. Return to main app
 */
@AndroidEntryPoint
class WebViewLoginActivity : ComponentActivity() {
    
    @Inject
    lateinit var cookieJar: PersistentCookieJar
    
    @Inject
    lateinit var userPreferences: UserPreferences
    
    private var isLoginSuccessful by mutableStateOf(false)
    private var isLoading by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)
    
    companion object {
        private const val LOGIN_URL = "https://personal.neu.edu.cn/portal/"
        private const val CAS_DOMAIN = "pass.neu.edu.cn"
        private const val PERSONAL_DOMAIN = "personal.neu.edu.cn"
        private const val ECODE_DOMAIN = "ecode.neu.edu.cn"
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            NeuECodeTheme {
                WebViewLoginScreen(
                    isLoading = isLoading,
                    isLoginSuccessful = isLoginSuccessful,
                    errorMessage = errorMessage,
                    onClose = { finish() },
                    onWebViewReady = { webView ->
                        setupWebView(webView)
                        webView.loadUrl(LOGIN_URL)
                    }
                )
            }
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(false)
            userAgentString = "ZhilinEai ZhilinNeuApp/3.1.1 (Android)"
        }
        
        // Enable cookie syncing
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                
                url?.let {
                    Timber.d("WebView loaded: $url")
                    
                    // Check if we're on a success page
                    if (it.contains(PERSONAL_DOMAIN) && !it.contains("/cas/login")) {
                        checkLoginSuccess(it)
                    }
                }
            }
            
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                
                Timber.d("WebView navigating: $url")
                
                // Check for ticket parameter (CAS success callback)
                if (url.contains("ticket=ST-")) {
                    handleTicketCallback(url)
                    return false
                }
                
                // Allow navigation
                return false
            }
        }
    }
    
    private fun handleTicketCallback(url: String) {
        Timber.i("Detected CAS ticket callback")
        isLoading = true
        
        lifecycleScope.launch {
            try {
                // Extract ticket from URL
                val uri = Uri.parse(url)
                val ticket = uri.getQueryParameter("ticket")
                
                if (ticket.isNullOrEmpty()) {
                    Timber.w("Ticket parameter is empty")
                    errorMessage = "登录票据获取失败"
                    isLoading = false
                    return@launch
                }
                
                Timber.i("Extracted CAS ticket")
                
                // Sync cookies from WebView to OkHttp
                syncCookiesFromWebView()
                
                // Save ticket
                userPreferences.saveLoginTicket(ticket)
                
                // Mark success
                isLoginSuccessful = true
                isLoading = false
                
                // Close activity after a short delay
                kotlinx.coroutines.delay(1000)
                setResult(RESULT_OK)
                finish()
                
            } catch (e: Exception) {
                Timber.e(e, "Error handling ticket callback")
                errorMessage = "登录处理失败: ${e.message}"
                isLoading = false
            }
        }
    }
    
    private fun checkLoginSuccess(url: String) {
        lifecycleScope.launch {
            try {
                // Check if we have the required cookies
                val cookieManager = CookieManager.getInstance()
                val cookieString = cookieManager.getCookie(PERSONAL_DOMAIN)
                
                if (cookieString != null && 
                    (cookieString.contains("CK_LC=") || cookieString.contains("SESS_ID="))) {
                    
                    Timber.i("Login successful, syncing cookies")
                    
                    // Sync all cookies
                    syncCookiesFromWebView()
                    
                    isLoginSuccessful = true
                    
                    // Close activity
                    kotlinx.coroutines.delay(1000)
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking login success")
            }
        }
    }
    
    /**
     * Sync cookies from WebView CookieManager to OkHttp CookieJar
     * 
     * Syncs cookies for:
     * - personal.neu.edu.cn (SESS_ID, CK_LC, CK_VL)
     * - pass.neu.edu.cn (CASTGC)
     * - ecode.neu.edu.cn (app-specific cookies)
     */
    private fun syncCookiesFromWebView() {
        val cookieManager = CookieManager.getInstance()
        
        listOf(PERSONAL_DOMAIN, CAS_DOMAIN, ECODE_DOMAIN).forEach { domain ->
            val cookieString = cookieManager.getCookie(domain)
            
            if (cookieString != null) {
                Timber.d("Syncing cookies for $domain: ${summarizeCookieNames(cookieString)}")
                
                val cookies = parseCookieString(cookieString, domain)
                val httpUrl = "https://$domain/".toHttpUrl()
                
                // Save to OkHttp CookieJar
                cookies.forEach { cookie ->
                    cookieJar.saveFromResponse(httpUrl, listOf(cookie))
                    Timber.d("Saved cookie: ${cookie.name}=<redacted>")
                }
            }
        }
    }
    
    private fun summarizeCookieNames(cookieString: String): String {
        return cookieString.split(';')
            .mapNotNull { part -> part.substringBefore('=').trim().takeIf { it.isNotBlank() } }
            .distinct()
            .joinToString(prefix = "[", postfix = "]")
    }

    /**
     * Parse WebView cookie string into OkHttp Cookie objects
     */
    private fun parseCookieString(cookieString: String, domain: String): List<Cookie> {
        return cookieString.split("; ").mapNotNull { part ->
            val (name, value) = part.split("=", limit = 2).let {
                if (it.size == 2) it[0] to it[1] else return@mapNotNull null
            }
            
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path("/")
                .secure()
                .httpOnly()
                .build()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewLoginScreen(
    isLoading: Boolean,
    isLoginSuccessful: Boolean,
    errorMessage: String?,
    onClose: () -> Unit,
    onWebViewReady: (WebView) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统一身份认证") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).also { webView ->
                        onWebViewReady(webView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Success message
            if (isLoginSuccessful) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "登录成功",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("正在返回...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            
            // Error message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
