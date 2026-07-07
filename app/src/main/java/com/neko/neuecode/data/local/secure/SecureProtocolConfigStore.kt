package com.neko.neuecode.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.neko.neuecode.data.remote.config.ProtocolConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore-backed cache for remotely fetched protocol configuration.
 *
 * The cache keeps the app usable during short helper-backend outages while still
 * allowing key rotation from the backend. It stores only the app protocol config,
 * never NEU user credentials.
 */
@Singleton
class SecureProtocolConfigStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "neu_secure_protocol_config"
        private const val FALLBACK_PREFS_NAME = "neu_secure_protocol_config_fallback"
        private const val KEY_CONFIG_JSON = "protocol_config_json"
    }

    private val gson = Gson()
    private val prefs: SharedPreferences by lazy { createPreferences() }

    private fun createPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Encrypted protocol config store unavailable; using private fallback prefs")
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun save(config: ProtocolConfig) {
        prefs.edit()
            .putString(KEY_CONFIG_JSON, gson.toJson(config))
            .apply()
        Timber.i("Saved protocol config cache: keyVersion=${config.keyVersion}")
    }

    fun load(): ProtocolConfig? {
        val json = prefs.getString(KEY_CONFIG_JSON, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return try {
            gson.fromJson(json, ProtocolConfig::class.java)
        } catch (e: Exception) {
            Timber.w(e, "Protocol config cache is corrupt; clearing")
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        Timber.i("Cleared protocol config cache")
    }
}
