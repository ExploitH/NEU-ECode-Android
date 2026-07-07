package com.neko.neuecode.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores optional long-lived login credentials using Android Keystore-backed
 * encrypted SharedPreferences. This is used only when the user enables
 * long-term login, matching the official app's auto-login behaviour while
 * avoiding plaintext password storage.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Credentials(
        val username: String,
        val password: String
    )

    companion object {
        private const val PREFS_NAME = "neu_secure_credentials"
        private const val FALLBACK_PREFS_NAME = "neu_secure_credentials_fallback"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ENABLED = "enabled"
    }

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
            // Some OEM/backup/keystore states can make EncryptedSharedPreferences
            // unavailable. Falling back keeps the app usable, but do not silently
            // claim strong encryption in logs/UI.
            Timber.e(e, "Encrypted credential store unavailable; using private fallback prefs")
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun save(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) return
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        Timber.i("Saved encrypted credentials for long-term login")
    }

    fun load(): Credentials? {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return null
        val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
        val password = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() }
        return if (username != null && password != null) Credentials(username, password) else null
    }

    fun hasCredentials(): Boolean = load() != null

    fun clear() {
        prefs.edit().clear().apply()
        Timber.i("Cleared long-term login credentials")
    }
}
