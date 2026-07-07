package com.neko.neuecode.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.neko.neuecode.data.remote.update.ApkDownloadInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun downloadApk(downloadInfo: ApkDownloadInfo): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, sanitizeFileName(downloadInfo.fileName))

        if (targetFile.exists() && downloadInfo.sha256.isNotBlank()) {
            val existingSha = sha256(targetFile)
            if (existingSha.equals(downloadInfo.sha256, ignoreCase = true)) {
                Timber.i("Reuse previously downloaded APK: ${targetFile.absolutePath}")
                return@withContext targetFile
            }
            targetFile.delete()
        }

        val client = okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(downloadInfo.downloadUrl)
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("下载 APK 失败：HTTP ${response.code}")
            }
            val body = response.body ?: error("下载 APK 响应为空")
            FileOutputStream(targetFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }

        if (downloadInfo.sha256.isNotBlank()) {
            val actualSha = sha256(targetFile)
            if (!actualSha.equals(downloadInfo.sha256, ignoreCase = true)) {
                targetFile.delete()
                throw IllegalStateException("APK 校验失败：SHA-256 不匹配")
            }
        }

        if (downloadInfo.size > 0L && targetFile.length() != downloadInfo.size) {
            Timber.w("APK size mismatch: expected=${downloadInfo.size}, actual=${targetFile.length()}")
        }

        Timber.i("APK downloaded to ${targetFile.absolutePath}")
        targetFile
    }

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        val uri = Uri.parse("package:${context.packageName}")
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installDownloadedApk(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun sanitizeFileName(fileName: String): String {
        val clean = fileName
            .trim()
            .ifBlank { "NEU-ECode-update.apk" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (clean.endsWith(".apk", ignoreCase = true)) clean else "$clean.apk"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
