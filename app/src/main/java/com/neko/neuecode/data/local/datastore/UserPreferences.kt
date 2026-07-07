package com.neko.neuecode.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.neko.neuecode.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "neu_user_preferences"
)

/**
 * User preferences storage using DataStore
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val NAME = stringPreferencesKey("name")
        val STUDENT_ID = stringPreferencesKey("student_id")
        val DEPARTMENT = stringPreferencesKey("department")
        val AVATAR = stringPreferencesKey("avatar")
        val EMAIL = stringPreferencesKey("email")
        
        val TGT = stringPreferencesKey("tgt")
        val LOGIN_TICKET = stringPreferencesKey("login_ticket")
        val LAST_REFRESH = longPreferencesKey("last_refresh")
        val AUTO_LOGIN_ENABLED = booleanPreferencesKey("auto_login_enabled")
        
        val SAVED_USERNAME = stringPreferencesKey("saved_username")
        val SAVED_PASSWORD = stringPreferencesKey("saved_password")
    }
    
    // User info flow
    val userFlow: Flow<User?> = context.userDataStore.data.map { prefs ->
        val userId = prefs[Keys.USER_ID]
        val username = prefs[Keys.USERNAME]
        val name = prefs[Keys.NAME]
        
        if (userId != null && username != null && name != null) {
            User(
                userId = userId,
                username = username,
                name = name,
                studentId = prefs[Keys.STUDENT_ID],
                department = prefs[Keys.DEPARTMENT],
                avatar = prefs[Keys.AVATAR],
                email = prefs[Keys.EMAIL]
            )
        } else {
            null
        }
    }
    
    // Save user info
    suspend fun saveUser(user: User) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.userId
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.NAME] = user.name
            user.studentId?.let { prefs[Keys.STUDENT_ID] = it }
            user.department?.let { prefs[Keys.DEPARTMENT] = it }
            user.avatar?.let { prefs[Keys.AVATAR] = it }
            user.email?.let { prefs[Keys.EMAIL] = it }
        }
        Timber.d("Saved user: ${user.username}")
    }
    
    // Get user (suspend version)
    suspend fun getUser(): User? {
        return userFlow.first()
    }
    
    // Clear user info
    suspend fun clearUser() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.NAME)
            prefs.remove(Keys.STUDENT_ID)
            prefs.remove(Keys.DEPARTMENT)
            prefs.remove(Keys.AVATAR)
            prefs.remove(Keys.EMAIL)
        }
        Timber.d("Cleared user info")
    }
    
    // TGT (Ticket Granting Ticket) management
    suspend fun saveTgt(tgt: String) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.TGT] = tgt
        }
        Timber.d("Saved TGT")
    }
    
    suspend fun getTgt(): String? {
        return context.userDataStore.data.first()[Keys.TGT]
    }
    
    suspend fun clearTgt() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.TGT)
        }
    }
    
    // Login ticket management
    suspend fun saveLoginTicket(ticket: String) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.LOGIN_TICKET] = ticket
        }
    }
    
    suspend fun getLoginTicket(): String? {
        return context.userDataStore.data.first()[Keys.LOGIN_TICKET]
    }
    
    // Last refresh timestamp
    suspend fun updateLastRefresh(timestamp: Long = System.currentTimeMillis()) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.LAST_REFRESH] = timestamp
        }
    }
    
    suspend fun getLastRefresh(): Long {
        return context.userDataStore.data.first()[Keys.LAST_REFRESH] ?: 0L
    }
    
    // Auto login
    suspend fun setAutoLoginEnabled(enabled: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOGIN_ENABLED] = enabled
        }
    }
    
    suspend fun isAutoLoginEnabled(): Boolean {
        return context.userDataStore.data.first()[Keys.AUTO_LOGIN_ENABLED] ?: false
    }
    
    suspend fun clearLegacySavedPassword() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.SAVED_PASSWORD)
        }
    }
    
    // Individual credential methods (for convenience)
    suspend fun saveUsername(username: String) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.SAVED_USERNAME] = username
        }
    }
    
    suspend fun getUsername(): String? {
        return context.userDataStore.data.first()[Keys.SAVED_USERNAME]
    }
    
    suspend fun clearPassword() = clearLegacySavedPassword()
    
    // Clear all data
    suspend fun clearAll() {
        context.userDataStore.edit { it.clear() }
        Timber.i("Cleared all user preferences")
    }
}
