package com.neko.neuecode.data.local.cookie

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cookieDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "neu_cookie_store"
)

/**
 * Cookie persistent storage using DataStore.
 * Serializes cookies to JSON and persists them across app restarts.
 */
@Singleton
class CookieSerializer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val cookieKey = stringPreferencesKey("cookies_json")
    private val lastSavedKey = stringPreferencesKey("last_saved_at")
    
    suspend fun saveCookies(cookies: List<SerializableCookie>) {
        try {
            val jsonString = json.encodeToString(cookies)
            context.cookieDataStore.edit { prefs ->
                prefs[cookieKey] = jsonString
                prefs[lastSavedKey] = System.currentTimeMillis().toString()
            }
            Timber.d("Saved ${cookies.size} cookies to persistent storage")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save cookies")
        }
    }
    
    suspend fun loadCookies(): List<SerializableCookie> {
        return try {
            context.cookieDataStore.data
                .map { prefs ->
                    prefs[cookieKey]?.let { jsonString ->
                        json.decodeFromString<List<SerializableCookie>>(jsonString)
                    } ?: emptyList()
                }
                .first()
                .filterNot { it.isExpired() }
                .also { cookies ->
                    Timber.d("Loaded ${cookies.size} cookies from persistent storage")
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load cookies")
            emptyList()
        }
    }
    
    suspend fun clearCookies() {
        try {
            context.cookieDataStore.edit { prefs ->
                prefs.remove(cookieKey)
                prefs.remove(lastSavedKey)
            }
            Timber.d("Cleared all cookies from persistent storage")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cookies")
        }
    }
    
    suspend fun getLastSavedAt(): Long {
        return try {
            context.cookieDataStore.data
                .map { prefs ->
                    prefs[lastSavedKey]?.toLongOrNull() ?: 0L
                }
                .first()
        } catch (e: Exception) {
            0L
        }
    }
}
