package com.neko.neuecode.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ECodeQrRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cookieJar: com.neko.neuecode.data.local.cookie.PersistentCookieJar
) {
    companion object {
        private const val ECODE_HOME_URL = "https://ecode.neu.edu.cn/ecode/#/"
        private const val ECODE_SERVICE_BRIDGE_URL = "https://pass.neu.edu.cn/tpass/login?service=https%3A%2F%2Fecode.neu.edu.cn%2Fecode%2Fapi%2Fsso%2Flogin"
        private const val PAGE_TIMEOUT_MS = 25000L
        private val EXTRACTION_DELAYS_MS = longArrayOf(1200L, 2600L, 4500L, 7000L, 10000L)

        private const val QR_EXTRACT_JS = """
            (function() {
              function canvasData() {
                var canvases = Array.from(document.querySelectorAll('canvas')).filter(function(c) {
                  return (c.width || 0) >= 80 && (c.height || 0) >= 80;
                }).sort(function(a, b) {
                  return (b.width * b.height) - (a.width * a.height);
                });
                if (canvases.length > 0) {
                  try { return canvases[0].toDataURL('image/png'); } catch (e) {}
                }
                return null;
              }
              function imageData() {
                var imgs = Array.from(document.images).filter(function(img) {
                  return ((img.naturalWidth || img.width || 0) >= 80) && ((img.naturalHeight || img.height || 0) >= 80);
                }).sort(function(a, b) {
                  return ((b.naturalWidth || b.width || 0) * (b.naturalHeight || b.height || 0)) - ((a.naturalWidth || a.width || 0) * (a.naturalHeight || a.height || 0));
                });
                for (var i = 0; i < imgs.length; i++) {
                  var img = imgs[i];
                  try {
                    var src = img.src || '';
                    if (src.startsWith('data:image/')) return src;
                    var w = img.naturalWidth || img.width;
                    var h = img.naturalHeight || img.height;
                    var c = document.createElement('canvas');
                    c.width = w; c.height = h;
                    var ctx = c.getContext('2d');
                    ctx.drawImage(img, 0, 0, w, h);
                    return c.toDataURL('image/png');
                  } catch (e) {}
                }
                return null;
              }
              return canvasData() || imageData() || null;
            })();
        """
    }

    suspend fun fetchQrBitmap(): Bitmap? {
        cookieJar.restoreFromStorage()
        return withContext(Dispatchers.Main) {
            captureQrBitmapOnMain()
        }
    }

    fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun captureQrBitmapOnMain(): Bitmap? = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val cookieManager = CookieManager.getInstance()
        val webView = WebView(context)
        var completed = false
        var currentUrl: String? = null

        fun finish(result: Bitmap?) {
            if (completed) return
            completed = true
            try {
                webView.stopLoading()
            } catch (_: Exception) {}
            handler.post {
                try {
                    webView.destroy()
                } catch (_: Exception) {}
            }
            if (cont.isActive) cont.resume(result)
        }

        fun decodeJsString(raw: String?): String? {
            if (raw == null || raw == "null") return null
            return try {
                when (val value = JSONTokener(raw).nextValue()) {
                    is String -> value
                    else -> value?.toString()
                }
            } catch (_: Exception) {
                raw.removePrefix("\"").removeSuffix("\"")
                    .replace("\\/", "/")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
            }
        }

        fun decodeDataUrl(dataUrl: String): Bitmap? {
            val base64Part = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
            if (base64Part.isBlank()) return null
            return try {
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode QR data URL")
                null
            }
        }

        fun tryExtractQr(reason: String) {
            if (completed) return
            Timber.d("Trying QR extract: $reason from $currentUrl")
            webView.evaluateJavascript(QR_EXTRACT_JS) { raw ->
                val decoded = decodeJsString(raw)
                val bitmap = decoded?.takeIf { it.startsWith("data:image/") }?.let { decodeDataUrl(it) }
                if (bitmap != null) {
                    Timber.i("QR extracted successfully via $reason")
                    finish(bitmap)
                }
            }
        }

        val timeoutRunnable = Runnable {
            Timber.w("QR capture timed out at $currentUrl")
            finish(null)
        }
        handler.postDelayed(timeoutRunnable, PAGE_TIMEOUT_MS)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = "ZhilinEai ZhilinNeuApp/3.1.1"
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, 1080, 1920)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val safeUrl = url ?: return
                currentUrl = safeUrl
                Timber.d("QR capture WebView finished: $safeUrl")

                if (
                    safeUrl.contains("ecode.neu.edu.cn/ecode/api/sso/login") ||
                    safeUrl == "https://ecode.neu.edu.cn/ecode/api" ||
                    safeUrl == "https://ecode.neu.edu.cn/ecode/api/"
                ) {
                    Timber.i("QR capture reached ecode bridge/API landing, forwarding to final page")
                    webView.loadUrl(ECODE_HOME_URL)
                    return
                }

                if (safeUrl.startsWith(ECODE_HOME_URL) || safeUrl.startsWith("https://ecode.neu.edu.cn/ecode/")) {
                    EXTRACTION_DELAYS_MS.forEach { delayMs ->
                        handler.postDelayed({ tryExtractQr("delay-${delayMs}ms") }, delayMs)
                    }
                }
            }
        }

        cont.invokeOnCancellation {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (_: Exception) {}
        }

        Timber.i("Starting hidden WebView QR capture")
        webView.loadUrl(ECODE_SERVICE_BRIDGE_URL)
    }
}
