package com.neko.neuecode.data.local.notice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neko.neuecode.domain.notice.AppNotice
import com.neko.neuecode.domain.notice.NoticeInbox
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.noticeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "neu_app_notices",
)

@Singleton
class NoticeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()
    private val noticesType = object : TypeToken<List<AppNotice>>() {}.type

    val noticesFlow: Flow<List<AppNotice>> = context.noticeDataStore.data.map { prefs ->
        decodeNotices(prefs[Keys.NOTICES_JSON].orEmpty())
    }

    val readIdsFlow: Flow<Set<String>> = context.noticeDataStore.data.map { prefs ->
        prefs[Keys.READ_IDS].orEmpty()
    }

    suspend fun cachedNotices(): List<AppNotice> = noticesFlow.first()

    suspend fun readIds(): Set<String> = readIdsFlow.first()

    suspend fun replaceNotices(notices: List<AppNotice>) {
        context.noticeDataStore.edit { prefs ->
            prefs[Keys.NOTICES_JSON] = gson.toJson(notices)
        }
        Timber.d("Cached %d app notices", notices.size)
    }

    suspend fun markRead(noticeId: String) {
        if (noticeId.isBlank()) return
        context.noticeDataStore.edit { prefs ->
            prefs[Keys.READ_IDS] = NoticeInbox.markRead(
                notices = decodeNotices(prefs[Keys.NOTICES_JSON].orEmpty()),
                readIds = prefs[Keys.READ_IDS].orEmpty(),
                noticeId = noticeId,
            )
        }
    }

    suspend fun markAllRead() {
        context.noticeDataStore.edit { prefs ->
            val notices = decodeNotices(prefs[Keys.NOTICES_JSON].orEmpty())
            prefs[Keys.READ_IDS] = NoticeInbox.markAllRead(notices, prefs[Keys.READ_IDS].orEmpty())
        }
    }

    private fun decodeNotices(raw: String): List<AppNotice> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<AppNotice>>(raw, noticesType).orEmpty()
        }.onFailure {
            Timber.w(it, "Failed to decode cached notices")
        }.getOrDefault(emptyList())
    }

    private object Keys {
        val NOTICES_JSON = stringPreferencesKey("notices_json")
        val READ_IDS = stringSetPreferencesKey("read_ids")
    }
}
