package com.neko.neuecode.data.local.cookie

import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.HttpCookie

/**
 * Serializable Cookie wrapper for persistent storage.
 * Inspired by the official 智慧东大 app's SerializableCookie implementation.
 */
@Serializable
data class SerializableCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAt: Long = 0L,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val hostOnly: Boolean = false,
    val persistent: Boolean = false,
    val fromWebViewSnapshot: Boolean = false
) {
    
    companion object {
        fun fromOkHttpCookie(cookie: Cookie): SerializableCookie {
            return SerializableCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly,
                persistent = cookie.persistent
            )
        }
        
        fun fromSetCookieString(url: String, setCookieValue: String): SerializableCookie? {
            return try {
                val httpCookie = HttpCookie.parse(setCookieValue).firstOrNull() ?: return null
                SerializableCookie(
                    name = httpCookie.name,
                    value = httpCookie.value,
                    domain = httpCookie.domain ?: extractDomain(url),
                    path = httpCookie.path ?: "/",
                    expiresAt = if (httpCookie.maxAge > 0) {
                        System.currentTimeMillis() + (httpCookie.maxAge * 1000L)
                    } else 0L,
                    secure = httpCookie.secure,
                    // HttpCookie.isHttpOnly requires API 24. Parsing the attribute
                    // directly keeps this model safe on the app's minSdk 23.
                    httpOnly = setCookieValue
                        .split(';')
                        .drop(1)
                        .any { it.trim().equals("HttpOnly", ignoreCase = true) },
                    hostOnly = httpCookie.domain == null,
                    persistent = httpCookie.maxAge > 0
                )
            } catch (e: Exception) {
                null
            }
        }
        
        private fun extractDomain(url: String): String {
            return try {
                val host = url.substringAfter("://").substringBefore("/")
                if (host.contains(":")) host.substringBefore(":") else host
            } catch (e: Exception) {
                ""
            }
        }
    }
    
    fun toOkHttpCookie(): Cookie? {
        return try {
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path(path)
                .apply {
                    if (expiresAt > 0) expiresAt(expiresAt)
                    if (secure) secure()
                    if (httpOnly) httpOnly()
                    if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                }
                .build()
        } catch (e: Exception) {
            null
        }
    }
    
    fun toCookieHeaderValue(): String {
        return "$name=$value"
    }
    
    fun isExpired(): Boolean {
        return expiresAt > 0 && expiresAt < System.currentTimeMillis()
    }
    
    fun matches(url: String): Boolean {
        if (isExpired()) return false
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return toOkHttpCookie()?.matches(httpUrl) == true
    }

    fun hasSameIdentity(other: SerializableCookie): Boolean =
        name == other.name &&
            domain.trimStart('.').equals(other.domain.trimStart('.'), ignoreCase = true) &&
            path == other.path
}
