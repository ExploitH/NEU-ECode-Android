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
    
    // In-memory cookie store for fast access
    private val cookieStore = ConcurrentHashMap<String, MutableSet<SerializableCookie>>()
    
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
        
        val domain = url.host
        val serializedCookies = cookies.mapNotNull { cookie ->
            try {
                SerializableCookie.fromOkHttpCookie(cookie)
            } catch (e: Exception) {
                Timber.w(e, "Failed to serialize cookie: ${cookie.name}")
                null
            }
        }
        
        if (serializedCookies.isEmpty()) return
        
        // Save to in-memory store
        val domainCookies = cookieStore.getOrPut(domain) { mutableSetOf() }
        synchronized(domainCookies) {
            // Remove old cookies with same name
            serializedCookies.forEach { newCookie ->
                domainCookies.removeIf { it.name == newCookie.name }
            }
            domainCookies.addAll(serializedCookies)
            // Remove expired cookies
            domainCookies.removeIf { it.isExpired() }
        }
        
        Timber.d("Saved ${serializedCookies.size} cookies for domain: $domain")
        
        // Sync to WebView (must be on main thread)
        syncToWebView(url.toString(), serializedCookies)
        
        // Persist to storage asynchronously
        scope.launch {
            persistToStorage()
        }
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
        
        return matchingCookies
    }
    
    /**
     * Restore cookies from persistent storage.
     * Should be called during app initialization.
     */
    suspend fun restoreFromStorage() {
        if (isRestored) return
        
        try {
            val savedCookies = cookieSerializer.loadCookies()
            
            // Group by domain
            savedCookies.groupBy { it.domain }.forEach { (domain, cookies) ->
                val domainCookies = cookieStore.getOrPut(domain) { mutableSetOf() }
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

        val domainCookies = cookieStore.getOrPut(domain) { mutableSetOf() }
        synchronized(domainCookies) {
            domainCookies.removeIf { it.name == cookie.name }
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
                    if (name.isNotBlank() && value.isNotBlank() && !value.equals("deleted", ignoreCase = true)) {
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
                                persistent = false
                            )
                        )
                    }
                }
        }

        if (imported.isEmpty()) return 0

        imported.groupBy { it.domain }.forEach { (domain, cookies) ->
            val domainCookies = cookieStore.getOrPut(domain) { mutableSetOf() }
            synchronized(domainCookies) {
                cookies.forEach { newCookie ->
                    domainCookies.removeIf { it.name == newCookie.name }
                }
                domainCookies.addAll(cookies)
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
}
