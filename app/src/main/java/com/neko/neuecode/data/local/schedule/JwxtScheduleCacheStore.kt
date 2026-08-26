package com.neko.neuecode.data.local.schedule

import android.content.Context
import com.google.gson.Gson
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JwxtScheduleCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()
    private val file: File
        get() = File(context.filesDir, "jwxt_schedule_cache.json")

    fun load(): JwxtScheduleDocument? {
        return try {
            if (!file.exists()) return null
            gson.fromJson(file.readText(Charsets.UTF_8), JwxtScheduleDocument::class.java)
        } catch (e: Exception) {
            Timber.w(e, "Failed to read local schedule cache")
            null
        }
    }

    fun save(document: JwxtScheduleDocument) {
        try {
            file.writeText(gson.toJson(document), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "Failed to write local schedule cache")
        }
    }
}
