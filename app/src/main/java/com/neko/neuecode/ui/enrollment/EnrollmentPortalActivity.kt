package com.neko.neuecode.ui.enrollment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonParser
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.remote.enrollment.EnrollmentPortalSession
import com.neko.neuecode.data.remote.enrollment.EnrollmentSessionHeaders
import com.neko.neuecode.data.remote.enrollment.EnrollmentSessionStore
import com.neko.neuecode.ui.theme.NeuECodeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EnrollmentPortalActivity : ComponentActivity() {
    @Inject
    lateinit var cookieJar: PersistentCookieJar

    @Inject
    lateinit var sessionStore: EnrollmentSessionStore

    private lateinit var webView: WebView
    private var statusText by mutableStateOf("正在进入选课系统")
    private var isCompleting by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContent {
            NeuECodeTheme {
                EnrollmentPortalScreen(
                    statusText = statusText,
                    isCompleting = isCompleting,
                    onClose = ::finish,
                    onWebViewReady = { view ->
                        webView = view
                        configureWebView(view)
                        view.loadUrl(START_URL)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return shouldBlockNavigation(request?.url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return shouldBlockNavigation(url?.let(Uri::parse))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val safeView = view ?: return
                val uri = url?.let(Uri::parse) ?: return
                if (!uri.host.equals(JWXK_HOST, ignoreCase = true)) {
                    statusText = "请完成统一身份认证"
                    return
                }
                statusText = if (uri.path?.contains("/elective/grablessons") == true) {
                    "正在同步当前选课轮次"
                } else {
                    "请选择轮次并进入课程列表"
                }
                EXTRACTION_DELAYS_MS.forEach { delayMs ->
                    safeView.postDelayed({ extractSession(safeView) }, delayMs)
                }
            }
        }
    }

    private fun shouldBlockNavigation(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "https") return true
        val host = uri.host.orEmpty().lowercase()
        return host != JWXK_HOST && host != PASS_HOST
    }

    private fun extractSession(view: WebView) {
        if (isCompleting || view.url?.let(Uri::parse)?.host != JWXK_HOST) return
        view.evaluateJavascript(EXTRACT_SESSION_JS) { raw ->
            val session = EnrollmentPortalSessionDecoder.decode(raw) ?: return@evaluateJavascript
            isCompleting = true
            statusText = "会话已同步，正在读取课程"
            lifecycleScope.launch {
                try {
                    cookieJar.snapshotFromWebView(listOf(JWXK_COOKIE_URL))
                    sessionStore.replace(session)
                    setResult(Activity.RESULT_OK)
                    finish()
                } catch (_: Exception) {
                    isCompleting = false
                    statusText = "会话同步失败，请重新进入当前轮次"
                }
            }
        }
    }

    companion object {
        private const val JWXK_HOST = "jwxk.neu.edu.cn"
        private const val PASS_HOST = "pass.neu.edu.cn"
        private const val JWXK_COOKIE_URL = "https://jwxk.neu.edu.cn/xsxk/"
        private const val START_URL =
            "https://pass.neu.edu.cn/tpass/login?service=https%3A%2F%2Fjwxk.neu.edu.cn%2Fxsxk%2Fauth%2Fcas"
        private val EXTRACTION_DELAYS_MS = listOf(250L, 900L, 1800L)

        fun createIntent(context: Context): Intent = Intent(context, EnrollmentPortalActivity::class.java)

        private val EXTRACT_SESSION_JS = """
            (() => {
              const text = (value) => String(value == null ? '' : value).trim();
              const parseStored = (key) => {
                try { return JSON.parse(sessionStorage.getItem(key) || 'null'); }
                catch (_) { return null; }
              };
              const normalizeToken = (raw) => {
                let value = text(raw);
                if (value.startsWith('"') && value.endsWith('"')) {
                  try { const parsed = JSON.parse(value); if (typeof parsed === 'string') value = parsed.trim(); }
                  catch (_) {}
                }
                return value;
              };
              const cookieValue = (name) => {
                const prefix = name + '=';
                const pair = document.cookie.split(';').map((part) => part.trim())
                  .find((part) => part.startsWith(prefix));
                if (!pair) return '';
                const value = pair.substring(prefix.length);
                try { return decodeURIComponent(value); } catch (_) { return value; }
              };
              const grab = window.grablessonsVue || document.querySelector('.grablessons')?.__vue__ || null;
              const profile = window.loginVue || document.querySelector('.indexPage')?.__vue__ || null;
              const storedBatch = parseStored('currentBatch');
              const batch = grab?.lcParam?.currentBatch || profile?.lcParam?.currentBatch || storedBatch || null;
              let token = normalizeToken(sessionStorage.getItem('token'));
              if (!token) token = normalizeToken(cookieValue('Authorization') || cookieValue('token'));
              if (!token) token = normalizeToken(window.axios?.defaults?.headers?.common?.Authorization
                || window.axios?.defaults?.headers?.Authorization);
              const params = new URLSearchParams(location.search);
              const batchId = text(params.get('batchId') || batch?.code);
              const menu = grab?.menuData?.menuList || grab?.lcParam?.currentBatch?.menuList || batch?.menuList || [];
              const courseTypes = [...new Set((Array.isArray(menu) ? menu : [])
                .map((item) => text(item?.teachingClassType).toUpperCase())
                .filter((item) => item && item !== 'YXKC' && item !== 'SCKC'))];
              const currentType = text(grab?.teachingClassType || sessionStorage.getItem('teachingClassType')).toUpperCase();
              if (currentType && currentType !== 'YXKC' && currentType !== 'SCKC' && !courseTypes.includes(currentType)) {
                courseTypes.push(currentType);
              }
              return JSON.stringify({
                ok: Boolean(token && batchId),
                authorization: token,
                batchId,
                batchName: text(batch?.name),
                typeCode: text(batch?.typeCode),
                campus: text(grab?.currentCampus?.code),
                courseTypes
              });
            })();
        """.trimIndent()
    }
}

internal object EnrollmentPortalSessionDecoder {
    fun decode(raw: String?): EnrollmentPortalSession? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val evaluated = JsonParser.parseString(raw)
            val json = if (evaluated.isJsonPrimitive && evaluated.asJsonPrimitive.isString) {
                evaluated.asString
            } else {
                evaluated.toString()
            }
            val root = JsonParser.parseString(json).asJsonObject
            if (root.get("ok")?.asBoolean != true) return null
            val authorization = root.string("authorization")
            val batchId = root.string("batchId")
            if (authorization.isBlank() || batchId.isBlank()) return null
            EnrollmentPortalSession(
                headers = EnrollmentSessionHeaders(authorization, batchId),
                batchName = root.string("batchName"),
                typeCode = root.string("typeCode"),
                campus = root.string("campus"),
                courseTypes = root.getAsJsonArray("courseTypes")
                    ?.mapNotNull { it.takeIf { item -> item.isJsonPrimitive }?.asString }
                    .orEmpty()
            )
        }.getOrNull()
    }

    private fun com.google.gson.JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnrollmentPortalScreen(
    statusText: String,
    isCompleting: Boolean,
    onClose: () -> Unit,
    onWebViewReady: (WebView) -> Unit
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步选课会话") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    if (isCompleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            AndroidView(
                factory = { context -> WebView(context).also(onWebViewReady) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}