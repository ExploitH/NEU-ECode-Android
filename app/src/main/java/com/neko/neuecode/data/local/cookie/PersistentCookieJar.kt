package com.neko.neuecode.data.local.cookie

import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import timber.log.Timber
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent CookieJar that syncs between OkHttp and WebView CookieManager.
 * 
 * Key features inspired by 智慧东大:
 * 1. Automatic serialization to persistent storage
 * 2. Bidirectional sync: OkHttp ↔ WebView
 * 3. In-memory cache for performance
 * 4. Automatic cleanup of expired cookies
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    private val cookieSerializer: CookieSerializer
) : CookieJar {
    
    // Identity is RFC-style name + domain + path. MutableList lets us replace
    // a rotating cookie synchronously before the next request is allowed to run.
    private val cookieStore = ConcurrentHashMap<String, MutableList<SerializableCookie>>()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    @Volatile
    private var isRestored = false
    
    init {
        // Restore cookies from persistent storage on initialization
        scope.launch {
            restoreFromStorage()
        }
    }
    
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return

        val serializedCookies = cookies.mapNotNull { cookie ->
            try {
                SerializableCookie.fromOkHttpCookie(cookie)
            } catch (e: Exception) {
                Timber.w(e, "Failed to serialize cookie: ${cookie.name}")
                null
            }
        }
        if (serializedCookies.isEmpty()) return

        serializedCookies.groupBy { normalizeDomain(it.domain) }.forEach { (domain, newCookies) ->
            val domainCookies = cookieStore.getOrPut(domain) { mutableListOf() }
            synchronized(domainCookies) {
                newCookies.forEach { newCookie ->
                    // Set-Cookie is authoritative. Replace the exact RFC identity,
                    // and remove metadata-less WebView placeholders for the name.
                    domainCookies.removeAll { stored ->
                        stored.hasSameIdentity(newCookie) ||
                            (stored.fromWebViewSnapshot &&
                                stored.name == newCookie.name &&
                                normalizeDomain(stored.domain) == normalizeDomain(newCookie.domain))
                    }
                    if (!newCookie.isExpired()) domainCookies.add(newCookie)
                }
                domainCookies.removeAll { it.isExpired() }
            }
        }

        Timber.d("Saved ${serializedCookies.size} cookies for response host: ${url.host}")
        syncToWebView(url.toString(), serializedCookies)
        scope.launch { persistToStorage() }
    }
    
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val matchingCookies = mutableListOf<Cookie>()
        val urlString = url.toString()
        
        // Check all domains for matching cookies
        cookieStore.forEach { (_, cookies) ->
            synchronized(cookies) {
                cookies.forEach { serializableCookie ->
                    if (serializableCookie.matches(urlString)) {
                        serializableCookie.toOkHttpCookie()?.let { cookie ->
                            matchingCookies.add(cookie)
                        }
                    }
                }
            }
        }
        
        if (matchingCookies.isNotEmpty()) {
            Timber.d("Loaded ${matchingCookies.size} cookies for request: ${url.host}")
        }

        return matchingCookies.sortedWith(
            compareByDescending<Cookie> { it.path.length }.thenBy { it.name }
        )
    }

    /**
     * Restore cookies from persistent storage.
     * Should be called during app initialization.
     */
    suspend fun restoreFromStorage() {
        if (isRestored) return
        
        try {
            val savedCookies = cookieSerializer.loadCookies()
            
            // Group by normalized domain so .example.edu.cn and example.edu.cn
            // share one storage bucket while retaining each cookie's identity.
            savedCookies.groupBy { normalizeDomain(it.domain) }.forEach { (domain, cookies) ->
                val domainCookies = cookieStore.getOrPut(domain) { mutableListOf() }
                synchronized(domainCookies) {
                    domainCookies.clear()
                    domainCookies.addAll(cookies)
                }
            }
            
            isRestored = true
            Timber.i("Restored ${savedCookies.size} cookies from storage")
            
            // Sync all cookies to WebView
            syncAllToWebView(savedCookies)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore cookies from storage")
        }
    }
    
    /**
     * Sync cookies to WebView CookieManager.
     * Key point: This is what makes login persist in WebView!
     */
    private fun syncToWebView(url: String, cookies: List<SerializableCookie>) {
        try {
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(true)
            
            cookies.forEach { cookie ->
                val cookieString = "${cookie.name}=${cookie.value}; " +
                        "Domain=${cookie.domain}; " +
                        "Path=${cookie.path}" +
                        (if (cookie.secure) "; Secure" else "") +
                        (if (cookie.httpOnly) "; HttpOnly" else "")
                
                manager.setCookie(url, cookieString)
            }
            
            manager.flush()
            Timber.d("Synced ${cookies.size} cookies to WebView for $url")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync cookies to WebView")
        }
    }
    
    private fun syncAllToWebView(cookies: List<SerializableCookie>) {
        try {
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(true)
            
            // Group by domain and sync
            cookies.groupBy { it.domain }.forEach { (domain, domainCookies) ->
                val url = "https://$domain/"
                domainCookies.forEach { cookie ->
                    manager.setCookie(url, cookie.toCookieHeaderValue())
                }
            }
            
            manager.flush()
            Timber.d("Synced all ${cookies.size} cookies to WebView")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync all cookies to WebView")
        }
    }
    
    /**
     * Persist all cookies to storage.
     */
    private suspend fun persistToStorage() {
        try {
            val allCookies = mutableListOf<SerializableCookie>()
            cookieStore.values.forEach { cookies ->
                synchronized(cookies) {
                    allCookies.addAll(cookies.filterNot { it.isExpired() })
                }
            }
            
            cookieSerializer.saveCookies(allCookies)
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist cookies to storage")
        }
    }
    
    /**
     * Clear all cookies.
     */
    suspend fun clearAll() {
        cookieStore.clear()
        cookieSerializer.clearCookies()
        
        // Clear WebView cookies
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear WebView cookies")
        }
        
        Timber.i("Cleared all cookies")
    }
    
    /**
     * Get cookies for a specific URL.
     */
    fun getCookiesForUrl(url: String): List<SerializableCookie> {
        val matchingCookies = mutableListOf<SerializableCookie>()
        
        cookieStore.values.forEach { cookies ->
            synchronized(cookies) {
                cookies.forEach { cookie ->
                    if (cookie.matches(url)) {
                        matchingCookies.add(cookie)
                    }
                }
            }
        }
        
        return matchingCookies
    }

    suspend fun saveManualCookie(
        url: String,
        name: String,
        value: String,
        domain: String,
        path: String = "/",
        secure: Boolean = true,
        httpOnly: Boolean = false
    ) {
        if (name.isBlank() || value.isBlank()) return

        val cookie = SerializableCookie(
            name = name,
            value = value,
            domain = domain,
            path = path,
            expiresAt = 0L,
            secure = secure,
            httpOnly = httpOnly,
            hostOnly = false,
            persistent = false
        )

        val domainCookies = cookieStore.getOrPut(normalizeDomain(domain)) { mutableListOf() }
        synchronized(domainCookies) {
            domainCookies.removeAll { it.hasSameIdentity(cookie) }
            domainCookies.add(cookie)
        }

        syncToWebView(url, listOf(cookie))
        persistToStorage()
        Timber.d("Saved manual cookie: $name for $domain")
    }

    /**
     * Import cookies currently owned by Android WebView's CookieManager.
     *
     * This is the critical reverse direction for the pragmatic NEU flow:
     * user logs in inside WebView/CAS -> WebView receives real business cookies
     * such as SESSION/SESS_ID/CK_LC -> we snapshot them into this CookieJar so
     * OkHttp, WorkManager and session checks see the same authenticated state.
     */
    suspend fun snapshotFromWebView(urls: List<String>): Int {
        val imported = mutableListOf<SerializableCookie>()
        val manager = CookieManager.getInstance()

        urls.forEach { url ->
            val cookieHeader = try {
                manager.getCookie(url)
            } catch (e: Exception) {
                Timber.w(e, "Failed to read WebView cookies for $url")
                null
            } ?: return@forEach

            val host = extractHost(url) ?: return@forEach
            cookieHeader.split(';')
                .map { it.trim() }
                .filter { it.contains('=') }
                .forEach { pair ->
                    val name = pair.substringBefore('=').trim()
                    val value = pair.substringAfter('=').trim()
                    if (name.isNotBlank() &&
                        name.lowercase() !in WEBVIEW_SESSION_HEADER_NAMES &&
                        value.isNotBlank() &&
                        !value.equals("deleted", ignoreCase = true)
                    ) {
                        imported.add(
                            SerializableCookie(
                                name = name,
                                value = value,
                                domain = host,
                                path = "/",
                                expiresAt = 0L,
                                secure = url.startsWith("https://", ignoreCase = true),
                                httpOnly = false,
                                hostOnly = true,
                                persistent = false,
                                fromWebViewSnapshot = true
                            )
                        )
                    }
                }
        }

        if (imported.isEmpty()) return 0

        imported.groupBy { normalizeDomain(it.domain) }.forEach { (domain, cookies) ->
            val domainCookies = cookieStore.getOrPut(domain) { mutableListOf() }
            synchronized(domainCookies) {
                cookies.forEach { snapshot ->
                    val existing = domainCookies.filter { stored ->
                        stored.name == snapshot.name &&
                            normalizeDomain(stored.domain) == normalizeDomain(snapshot.domain)
                    }
                    if (existing.isEmpty()) {
                        domainCookies.add(snapshot)
                    } else {
                        existing.forEach { stored ->
                            domainCookies.remove(stored)
                            domainCookies.add(stored.copy(value = snapshot.value))
                        }
                    }
                }
                domainCookies.removeAll { it.isExpired() }
            }
        }

        persistToStorage()
        Timber.i("Imported ${imported.size} WebView cookies into PersistentCookieJar")
        return imported.size
    }
    
    /**
     * Check if we have a specific cookie.
     */
    fun hasCookie(name: String): Boolean {
        return cookieStore.values.any { cookies ->
            synchronized(cookies) {
                cookies.any { it.name == name && !it.isExpired() }
            }
        }
    }

    private fun extractHost(url: String): String? {
        return try {
            URI(url).host
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeDomain(domain: String): String = domain.trimStart('.').lowercase()

    companion object {
        private val WEBVIEW_SESSION_HEADER_NAMES = setOf("authorization", "token")
    }
}
