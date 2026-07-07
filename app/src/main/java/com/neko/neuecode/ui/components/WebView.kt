package com.neko.neuecode.ui.components

import android.graphics.Bitmap
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compose wrapper for Android WebView
 * Handles cookie synchronization and navigation
 */
@Composable
fun NeuWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val webViewClient = remember {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    Timber.d("Page started: $it")
                    logWebViewEvent("PAGE_STARTED", it)
                    onPageStarted(it)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    Timber.d("Page finished: $it")
                    logWebViewEvent("PAGE_FINISHED", it)
                    onPageFinished(it)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val errorMsg = error?.description?.toString() ?: "Unknown error"
                Timber.e("WebView error: $errorMsg")
                onError(errorMsg)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                logWebViewEvent("SHOULD_OVERRIDE", url)
                // Let WebView handle all URLs
                return false
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                this.webViewClient = webViewClient
                
                val currentWebView = this
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    
                    // User agent
                    userAgentString = userAgentString + " NEU-eCode-Kotlin/4.0"
                    
                    // Zoom settings
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    
                    // Performance
                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                
                // Cookie settings
                android.webkit.CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(currentWebView, true)
                }
                
                Timber.d("Loading URL: $url")
                loadUrl(url)
            }
        },
        modifier = modifier,
        update = { webView ->
            if (webView.url != url) {
                Timber.d("Updating URL: $url")
                webView.loadUrl(url)
            }
        }
    )
}

/**
 * WebView with custom JavaScript injection
 */
@Composable
fun NeuWebViewWithJs(
    url: String,
    javascript: String? = null,
    modifier: Modifier = Modifier,
    onPageFinished: (String) -> Unit = {}
) {
    val webViewClient = remember {
        object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Inject custom JavaScript
                javascript?.let { js ->
                    view?.evaluateJavascript(js) { result ->
                        Timber.d("JS execution result: $result")
                    }
                }
                
                url?.let { onPageFinished(it) }
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                this.webViewClient = webViewClient
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                }
                
                val currentWebView = this
                android.webkit.CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(currentWebView, true)
                }
                
                loadUrl(url)
            }
        },
        modifier = modifier
    )
}

private fun logWebViewEvent(stage: String, url: String) {
    val cookieManager = CookieManager.getInstance()
    val personal = summarizeCookies(cookieManager.getCookie("https://personal.neu.edu.cn/"))
    val pass = summarizeCookies(cookieManager.getCookie("https://pass.neu.edu.cn/"))
    val ecode = summarizeCookies(cookieManager.getCookie("https://ecode.neu.edu.cn/"))
    val line = buildString {
        append("WEBVIEW ").append(stage).append('\n')
        append("URL: ").append(url).append('\n')
        append("Cookies(personal): ").append(personal).append('\n')
        append("Cookies(pass): ").append(pass).append('\n')
        append("Cookies(ecode): ").append(ecode)
    }
    Timber.tag("WebViewDiag").d(line)
    try {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "neuecode_network.log")
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        file.appendText("$ts $line\n")
    } catch (_: Exception) {
    }
}

private fun summarizeCookies(cookieHeader: String?): String {
    if (cookieHeader.isNullOrBlank()) return "-"
    return cookieHeader.split(';')
        .mapNotNull { part -> part.substringBefore('=').trim().takeIf { it.isNotBlank() } }
        .distinct()
        .joinToString(",")
}
