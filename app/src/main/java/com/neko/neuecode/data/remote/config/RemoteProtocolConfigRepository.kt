package com.neko.neuecode.data.remote.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.neko.neuecode.BuildConfig
import com.neko.neuecode.data.local.secure.SecureProtocolConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches public runtime configuration for the app from the helper backend.
 *
 * Protocol RSA material is intentionally not committed to the public GitHub tree.
 * The distributed APK fetches it from the app helper backend and caches it in
 * Android Keystore-backed encrypted preferences. The backend may later expose
 * update metadata through the same document.
 */
@Singleton
class RemoteProtocolConfigRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureStore: SecureProtocolConfigStore
) {
    companion object {
        private const val DEFAULT_TTL_SECONDS = 24 * 60 * 60L
    }

    suspend fun prefetch() {
        runCatching { getProtocolConfig(forceRefresh = false) }
            .onSuccess { Timber.i("Remote protocol config ready: keyVersion=${it.keyVersion}, privateKeyCount=${it.rsaPrivateKeysBase64.size}") }
            .onFailure { Timber.w(it, "Remote protocol config prefetch failed; will retry on demand") }
    }

    suspend fun getProtocolConfig(forceRefresh: Boolean = false): ProtocolConfig = withContext(Dispatchers.IO) {
        val cached = secureStore.load()
        if (!forceRefresh && cached != null && cached.isFresh()) {
            return@withContext cached
        }

        try {
            val fetched = fetchProtocolConfig()
            secureStore.save(fetched)
            fetched
        } catch (e: Exception) {
            if (cached != null) {
                Timber.w(e, "Using cached protocol config after refresh failure")
                cached
            } else {
                Timber.e(e, "No protocol config available")
                throw ProtocolConfigUnavailableException("无法获取 App 协议配置，请检查网络或稍后重试", e)
            }
        }
    }

    private fun fetchProtocolConfig(): ProtocolConfig {
        val base = BuildConfig.ECHELP_BASE_URL.trimEnd('/')
        val url = buildString {
            append(base)
            append("/api/v1/config/android")
            append("?versionCode=").append(BuildConfig.VERSION_CODE)
            append("&versionName=").append(urlEncode(BuildConfig.VERSION_NAME))
            append("&packageName=").append(urlEncode(BuildConfig.APPLICATION_ID))
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "NEU-eCode/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("helper backend HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val root = JsonParser.parseString(body).asJsonObject
            return parseProtocolConfig(root)
        }
    }

    private fun parseProtocolConfig(root: JsonObject): ProtocolConfig {
        val now = System.currentTimeMillis()
        val ttlSeconds = root.longOrNull("ttl_seconds") ?: DEFAULT_TTL_SECONDS
        val protocol = root.getAsJsonObject("protocol")
            ?: throw IllegalStateException("missing protocol config")

        val publicKey = protocol.stringOrNull("rsa_public_key_base64")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("missing rsa public key")

        val privateKeys = protocol.stringList("rsa_private_keys_base64")
        if (privateKeys.isEmpty()) {
            throw IllegalStateException("missing rsa private keys")
        }

        val keyVersion = protocol.intOrNull("key_version") ?: 1
        val config = ProtocolConfig(
            keyVersion = keyVersion,
            rsaPublicKeyBase64 = publicKey,
            rsaPrivateKeysBase64 = privateKeys,
            fetchedAtMillis = now,
            expiresAtMillis = now + ttlSeconds.coerceAtLeast(60L) * 1000L,
            appUpdate = root.getAsJsonObject("app")?.let { parseAppUpdate(it) }
        )

        Timber.i("Fetched remote protocol config: keyVersion=${config.keyVersion}, privateKeyCount=${config.rsaPrivateKeysBase64.size}, ttlSeconds=$ttlSeconds")
        return config
    }

    private fun parseAppUpdate(app: JsonObject): AppUpdateConfig {
        return AppUpdateConfig(
            latestVersionCode = app.intOrNull("latest_version_code") ?: 0,
            latestVersionName = app.stringOrNull("latest_version_name").orEmpty(),
            minSupportedVersionCode = app.intOrNull("min_supported_version_code") ?: 0,
            apkUrl = app.stringOrNull("apk_url").orEmpty(),
            releaseNotes = app.stringOrNull("release_notes").orEmpty()
        )
    }

    private fun JsonObject.stringOrNull(name: String): String? = get(name)?.takeIf { !it.isJsonNull }?.asString
    private fun JsonObject.intOrNull(name: String): Int? = get(name)?.takeIf { !it.isJsonNull }?.asInt
    private fun JsonObject.longOrNull(name: String): Long? = get(name)?.takeIf { !it.isJsonNull }?.asLong

    private fun JsonObject.stringList(name: String): List<String> {
        val element = get(name) ?: return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.toStringList()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                .split('|')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JsonArray.toStringList(): List<String> = mapNotNull { element ->
        element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

data class ProtocolConfig(
    val keyVersion: Int,
    val rsaPublicKeyBase64: String,
    val rsaPrivateKeysBase64: List<String>,
    val fetchedAtMillis: Long,
    val expiresAtMillis: Long,
    val appUpdate: AppUpdateConfig? = null
) {
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean = expiresAtMillis > nowMillis
}

data class AppUpdateConfig(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val apkUrl: String,
    val releaseNotes: String
)

class ProtocolConfigUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
