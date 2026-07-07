package com.neko.neuecode.ui.screen.recharge

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECHARGE_URL = "https://pay.neu.edu.cn/openPortal"
private const val RECHARGE_LOGIN_URL = "https://pay.neu.edu.cn/drCasLogin?redirectUrl=openBindInfo"
private const val PAY_HOST_PREFIX = "https://pay.neu.edu.cn"
private const val PAY_HTTP_HOST_PREFIX = "http://pay.neu.edu.cn"
private const val WX_CALLBACK = "https://pay.neu.edu.cn/wx/callback"
private const val APP_UA_SUFFIX = " NEU-eCode-Kotlin/5.33"

@Composable
fun RechargeScreen() {
    var currentUrl by rememberSaveable { mutableStateOf(RECHARGE_URL) }
    var statusText by rememberSaveable { mutableStateOf("等待页面加载") }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "充值",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentUrl.take(90),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = {
                    currentUrl = RECHARGE_URL
                    statusText = "重新加载充值页…"
                    isLoading = true
                    reloadKey += 1
                }) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }
        }

        key(reloadKey) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        val container = this
                        val mainWebView = WebView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        addView(mainWebView)

                        configureRechargeWebView(mainWebView)
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(mainWebView, true)
                            flush()
                        }

                        mainWebView.webChromeClient = createRechargeChromeClient(
                            container = container,
                            ownerWebView = mainWebView,
                            mainWebView = mainWebView,
                            setCurrentUrl = { currentUrl = it },
                            setStatusText = { statusText = it },
                            setLoading = { isLoading = it }
                        )

                        mainWebView.webViewClient = createRechargeWebViewClient(
                            ownerWebView = mainWebView,
                                isPopup = false,
                            setCurrentUrl = { currentUrl = it },
                            setStatusText = { statusText = it },
                            setLoading = { isLoading = it }
                        )

                        appendRechargeLog("RECHARGE_LOAD_INITIAL", RECHARGE_URL)
                        mainWebView.loadUrl(RECHARGE_URL)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { /* navigation is owned by WebView callbacks; avoid Compose reload loops */ }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureRechargeWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        if (!userAgentString.contains("NEU-eCode-Kotlin")) {
            userAgentString += APP_UA_SUFFIX
        }
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        useWideViewPort = true
        loadWithOverviewMode = true
        loadsImagesAutomatically = true
        blockNetworkImage = false
        mediaPlaybackRequiresUserGesture = false
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        allowContentAccess = true
        allowFileAccess = false
        textZoom = 100
        defaultFontSize = 16
        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
    }
    webView.isHorizontalScrollBarEnabled = false
    webView.isVerticalScrollBarEnabled = true
}

private fun createRechargeChromeClient(
    container: FrameLayout,
    ownerWebView: WebView,
    mainWebView: WebView,
    setCurrentUrl: (String) -> Unit,
    setStatusText: (String) -> Unit,
    setLoading: (Boolean) -> Unit
): WebChromeClient {
    return object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val sourceUrl = view?.url.orEmpty().ifBlank { mainWebView.url.orEmpty() }.ifBlank { RECHARGE_URL }
            val hitUrl = view?.hitTestResult?.extra.orEmpty()
            appendRechargeLog("RECHARGE_CREATE_WINDOW isDialog=$isDialog userGesture=$isUserGesture hit=${hitUrl.take(160)}", sourceUrl)

            if (hitUrl.isUsefulRechargeUrl()) {
                setStatusText("打开充值子页面…")
                setCurrentUrl(hitUrl)
                mainWebView.post { mainWebView.loadUrl(hitUrl) }
                return false
            }

            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val popupWebView = WebView(ownerWebView.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            configureRechargeWebView(popupWebView)
            CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true)
            popupWebView.webViewClient = createRechargeWebViewClient(
                ownerWebView = popupWebView,
                isPopup = true,
                setCurrentUrl = setCurrentUrl,
                setStatusText = setStatusText,
                setLoading = setLoading
            )
            popupWebView.webChromeClient = createRechargeChromeClient(
                container = container,
                ownerWebView = popupWebView,
                mainWebView = mainWebView,
                setCurrentUrl = setCurrentUrl,
                setStatusText = setStatusText,
                setLoading = setLoading
            )

            // Previous versions accepted target=_blank/window.open into a detached hidden WebView.
            // That can produce the exact symptom reported by the user: the shell/menu remains visible,
            // while the real application content is loaded in an invisible child window.  Keep the
            // popup WebView attached and visible so NEU Pay's internal window content can render.
            container.addView(popupWebView)
            popupWebView.bringToFront()
            popupWebView.requestFocus()
            setStatusText("打开充值子窗口…")
            setLoading(true)

            transport.webView = popupWebView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            appendRechargeLog("RECHARGE_CLOSE_WINDOW", window?.url ?: "-")
            if (window != null && window !== mainWebView) {
                container.removeView(window)
                window.destroy()
                mainWebView.bringToFront()
                setStatusText("充值页已返回")
                setLoading(false)
            } else {
                super.onCloseWindow(window)
            }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let {
                appendRechargeLog(
                    "RECHARGE_CONSOLE ${it.messageLevel()} ${it.sourceId()}:${it.lineNumber()}",
                    it.message().take(500)
                )
            }
            return super.onConsoleMessage(consoleMessage)
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (newProgress in listOf(25, 50, 75, 100)) {
                appendRechargeLog("RECHARGE_PROGRESS $newProgress", view?.url ?: "-")
            }
        }
    }
}

private fun createRechargeWebViewClient(
    ownerWebView: WebView,
    isPopup: Boolean,
    setCurrentUrl: (String) -> Unit,
    setStatusText: (String) -> Unit,
    setLoading: (Boolean) -> Unit
): WebViewClient {
    return object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let {
                setCurrentUrl(it)
                setStatusText(if (isPopup) "充值子页面加载中…" else "页面加载中…")
                setLoading(true)
                appendRechargeLog(if (isPopup) "RECHARGE_POPUP_STARTED" else "RECHARGE_PAGE_STARTED", it)
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            url?.let {
                setCurrentUrl(it)
                setStatusText(
                    when {
                        it.contains("openPortal") -> "充值页已加载"
                        it.contains("wx/callback") -> "支付回调页已返回"
                        isPopup -> "充值子页面已加载"
                        else -> "页面已加载"
                    }
                )
                setLoading(false)
                appendRechargeLog(if (isPopup) "RECHARGE_POPUP_FINISHED" else "RECHARGE_PAGE_FINISHED", it)
                if (!isPopup && it.isRechargeLoginShellUrl()) {
                    setStatusText("进入统一身份认证…")
                    appendRechargeLog("RECHARGE_AUTO_CAS_LOGIN", RECHARGE_LOGIN_URL)
                    view?.postDelayed({ view.loadUrl(RECHARGE_LOGIN_URL) }, 250)
                }
                view?.evaluateJavascript(RECHARGE_DOM_PROBE_JS) { result ->
                    appendRechargeLog(if (isPopup) "RECHARGE_POPUP_DOM" else "RECHARGE_PAGE_DOM", sanitizeJsResult(result))
                }
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val url = request?.url?.toString() ?: return false
            appendRechargeLog(if (isPopup) "RECHARGE_POPUP_OVERRIDE" else "RECHARGE_SHOULD_OVERRIDE", url)

            if (url.startsWith("https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb")) {
                val finalUrl = if (url.contains("redirect_url=")) {
                    url
                } else {
                    val joiner = if (url.contains("?")) "&" else "?"
                    url + joiner + "redirect_url=" + Uri.encode(WX_CALLBACK)
                }
                val headers = mapOf("Referer" to WX_CALLBACK)
                setStatusText("准备唤起微信支付…")
                ownerWebView.loadUrl(finalUrl, headers)
                appendRechargeLog("RECHARGE_WX_H5_BRIDGE", finalUrl)
                return true
            }

            if (url.isExternalAppUrl()) {
                val launched = launchExternal(ownerWebView.context, url)
                setStatusText(
                    if (launched) {
                        when {
                            url.startsWith("weixin://") -> "已尝试唤起微信支付"
                            url.startsWith("alipays://") || url.startsWith("alipay://") -> "已尝试唤起支付宝"
                            else -> "已尝试唤起外部应用"
                        }
                    } else {
                        "未能唤起外部支付应用"
                    }
                )
                return true
            }

            // Some NEU Pay pages still emit http://pay.neu.edu.cn subresources/pages.
            // Let the WebView load them after adding a scoped network-security exception.
            if (url.startsWith(PAY_HTTP_HOST_PREFIX) || url.startsWith(PAY_HOST_PREFIX)) {
                return false
            }

            return false
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            val url = request?.url?.toString().orEmpty()
            appendRechargeLog(
                "RECHARGE_WEB_ERROR main=${request?.isForMainFrame} code=${error?.errorCode} desc=${error?.description}",
                url
            )
            if (request?.isForMainFrame == true) {
                setStatusText("充值页加载异常：${error?.description ?: "未知错误"}")
                setLoading(false)
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            appendRechargeLog(
                "RECHARGE_HTTP_ERROR main=${request?.isForMainFrame} status=${errorResponse?.statusCode} reason=${errorResponse?.reasonPhrase}",
                request?.url?.toString().orEmpty()
            )
        }
    }
}

private fun String.isRechargeLoginShellUrl(): Boolean {
    return startsWith("https://pay.neu.edu.cn/tologin.html") ||
            startsWith("http://pay.neu.edu.cn/tologin.html")
}

private fun String.isUsefulRechargeUrl(): Boolean {
    if (isBlank()) return false
    if (equals("about:blank", ignoreCase = true)) return false
    if (startsWith("javascript:", ignoreCase = true)) return false
    return startsWith("http://", ignoreCase = true) ||
            startsWith("https://", ignoreCase = true) ||
            isExternalAppUrl()
}

private fun String.isExternalAppUrl(): Boolean {
    return startsWith("weixin://") ||
            startsWith("alipays://") ||
            startsWith("alipay://") ||
            startsWith("uppaywallet://") ||
            startsWith("upwrp://") ||
            startsWith("mailto:") ||
            startsWith("tel:") ||
            startsWith("intent://")
}

private fun launchExternal(context: android.content.Context, url: String): Boolean {
    return try {
        val intent = if (url.startsWith("intent://")) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No app can handle external url")
        false
    } catch (e: Exception) {
        Timber.e(e, "Failed to launch external payment app")
        false
    }
}

private const val RECHARGE_DOM_PROBE_JS = """
(function(){
  try {
    var body = document.body;
    var visibleText = body ? (body.innerText || '').replace(/\s+/g, ' ').slice(0, 260) : '';
    var iframeList = Array.prototype.slice.call(document.getElementsByTagName('iframe')).map(function(frame){
      return frame.src || frame.getAttribute('src') || '';
    }).slice(0, 8);
    var center = document.elementFromPoint(Math.floor(window.innerWidth / 2), Math.floor(window.innerHeight / 2));
    var data = {
      title: document.title || '',
      ready: document.readyState || '',
      url: location.href || '',
      bodyTextLen: body ? (body.innerText || '').length : -1,
      bodyHtmlLen: body ? (body.innerHTML || '').length : -1,
      bodyClient: body ? (body.clientWidth + 'x' + body.clientHeight) : '',
      bodyScroll: body ? (body.scrollWidth + 'x' + body.scrollHeight) : '',
      iframes: document.getElementsByTagName('iframe').length,
      iframeSrc: iframeList,
      frames: window.frames ? window.frames.length : 0,
      scripts: document.scripts ? document.scripts.length : 0,
      links: document.links ? document.links.length : 0,
      viewport: window.innerWidth + 'x' + window.innerHeight,
      centerTag: center ? (center.tagName + '#' + (center.id || '') + '.' + (center.className || '')) : '',
      sample: visibleText
    };
    return JSON.stringify(data);
  } catch (e) {
    return 'DOM_PROBE_ERROR:' + e.message;
  }
})();
"""

private fun sanitizeJsResult(result: String?): String {
    return result
        ?.replace("\\u003C", "<")
        ?.replace("\\n", " ")
        ?.take(900)
        ?: "-"
}

private fun appendRechargeLog(stage: String, url: String) {
    try {
        val cookieManager = CookieManager.getInstance()
        val pay = summarizeCookies(cookieManager.getCookie("https://pay.neu.edu.cn/"))
        val pass = summarizeCookies(cookieManager.getCookie("https://pass.neu.edu.cn/"))
        val line = buildString {
            append(stage).append('\n')
            append("URL: ").append(url).append('\n')
            append("Cookies(pay): ").append(pay).append('\n')
            append("Cookies(pass): ").append(pass)
        }
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
