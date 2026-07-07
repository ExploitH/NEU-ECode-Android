package com.neko.neuecode.util

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object CacheCleaner {
    data class CacheCleanResult(
        val bytesDeleted: Long,
        val filesDeleted: Int
    )

    suspend fun clearNonSessionCache(context: Context): CacheCleanResult = withContext(Dispatchers.IO) {
        var bytes = 0L
        var files = 0

        fun deleteRecursivelyCounting(file: File) {
            if (!file.exists()) return
            if (file.isFile) {
                val size = file.length()
                if (file.delete()) {
                    bytes += size
                    files += 1
                }
                return
            }
            file.listFiles()?.forEach(::deleteRecursivelyCounting)
            file.delete()
        }

        listOf(
            context.cacheDir,
            File(context.filesDir, "updates"),
            File(context.getExternalFilesDir(null), "Download"),
            File(context.getExternalFilesDir(null), "updates")
        ).filterNotNull().forEach(::deleteRecursivelyCounting)

        runCatching {
            WebStorage.getInstance().deleteAllData()
        }.onFailure { Timber.w(it, "Failed to clear WebStorage") }

        withContext(Dispatchers.Main) {
            runCatching {
                CookieManager.getInstance().flush()
                WebView(context.applicationContext).apply {
                    clearCache(true)
                    clearHistory()
                    destroy()
                }
            }.onFailure { Timber.w(it, "Failed to clear WebView cache") }
        }

        Timber.i("Cleared non-session cache: bytes=$bytes, files=$files")
        CacheCleanResult(bytesDeleted = bytes, filesDeleted = files)
    }
}
