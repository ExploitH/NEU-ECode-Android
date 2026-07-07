package com.neko.neuecode.data.remote.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.neko.neuecode.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun fetchLatestVersion(): AppVersionInfo = withContext(Dispatchers.IO) {
        val base = BuildConfig.ECHELP_BASE_URL.trimEnd('/')
        val url = buildString {
            append(base)
            append("/api/v1/app/version")
            append("?versionCode=").append(BuildConfig.VERSION_CODE)
            append("&versionName=").append(urlEncode(BuildConfig.VERSION_NAME))
            append("&packageName=").append(urlEncode(BuildConfig.APPLICATION_ID))
            append("&platform=android")
            append("&channel=stable")
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent())
            .build()

        executeJson(request) { root ->
            val latestVersionCode = root.intOrNull("latest_version_code") ?: 0
            val minSupportedVersionCode = root.intOrNull("min_supported_version_code") ?: 0
            val explicitUpdateRequired = root.boolOrNull("update_required")
            val explicitForceUpdate = root.boolOrNull("force_update")

            AppVersionInfo(
                latestVersionCode = latestVersionCode,
                latestVersionName = root.stringOrNull("latest_version_name").orEmpty(),
                minSupportedVersionCode = minSupportedVersionCode,
                releaseNotes = root.stringOrNull("release_notes").orEmpty(),
                updateRequired = explicitUpdateRequired ?: (latestVersionCode > BuildConfig.VERSION_CODE),
                forceUpdate = explicitForceUpdate ?: (minSupportedVersionCode > BuildConfig.VERSION_CODE)
            )
        }
    }

    suspend fun createUpdateSession(): UpdateSession = withContext(Dispatchers.IO) {
        val base = BuildConfig.ECHELP_BASE_URL.trimEnd('/')
        val url = "$base/api/v1/update/session"
        val payload = JSONObject().apply {
            put("platform", "android")
            put("channel", "stable")
            put("packageName", BuildConfig.APPLICATION_ID)
            put("currentVersionCode", BuildConfig.VERSION_CODE)
            put("currentVersionName", BuildConfig.VERSION_NAME)
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeJson(request) { root ->
            val verifyUrl = root.stringOrNull("verify_url") ?: root.stringOrNull("verifyUrl")
            if (verifyUrl.isNullOrBlank()) {
                throw AppUpdateException("更新验证地址缺失")
            }
            UpdateSession(
                state = root.stringOrNull("state").orEmpty(),
                verifyUrl = verifyUrl,
                expiresIn = root.longOrNull("expires_in") ?: 300L
            )
        }
    }

    suspend fun resolveDownloadInfo(claim: String): ApkDownloadInfo = withContext(Dispatchers.IO) {
        val base = BuildConfig.ECHELP_BASE_URL.trimEnd('/')
        val url = "$base/api/v1/apk/link"
        val payload = JSONObject().apply {
            put("claim", claim)
            put("platform", "android")
            put("channel", "stable")
            put("packageName", BuildConfig.APPLICATION_ID)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("versionName", BuildConfig.VERSION_NAME)
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeJson(request) { root ->
            val nestedDownload = root.getAsJsonObject("download")
            val downloadUrl = nestedDownload?.stringOrNull("url")
                ?: root.stringOrNull("download_url")
                ?: root.stringOrNull("apk_url")

            if (downloadUrl.isNullOrBlank()) {
                throw AppUpdateException("更新下载地址缺失")
            }

            val versionCode = root.intOrNull("version_code")
                ?: root.intOrNull("versionCode")
                ?: root.intOrNull("latest_version_code")
                ?: 0
            val versionName = root.stringOrNull("version_name")
                ?: root.stringOrNull("versionName")
                ?: root.stringOrNull("latest_version_name")
                ?: ""
            val expiresIn = nestedDownload?.longOrNull("expiresIn")
                ?: root.longOrNull("expires_in")
                ?: 180L
            val fileName = root.stringOrNull("file_name")
                ?: root.stringOrNull("fileName")
                ?: nestedDownload?.stringOrNull("fileName")
                ?: fallbackApkFileName(versionName, versionCode)

            ApkDownloadInfo(
                versionCode = versionCode,
                versionName = versionName,
                fileName = fileName,
                sha256 = root.stringOrNull("sha256").orEmpty(),
                size = root.longOrNull("size") ?: 0L,
                expiresIn = expiresIn,
                downloadUrl = downloadUrl
            )
        }
    }

    private inline fun <T> executeJson(request: Request, parser: (JsonObject) -> T): T {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val code = response.code
                val message = when (code) {
                    401, 403 -> "更新验证失败，请重新尝试"
                    404 -> "更新文件不存在"
                    409 -> "版本状态已变化，请重新检查更新"
                    426 -> "当前版本过旧，必须先升级"
                    429 -> "更新请求过于频繁，请稍后再试"
                    else -> "更新接口错误：HTTP $code"
                }
                throw AppUpdateException(message)
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                throw AppUpdateException("更新接口返回为空")
            }
            val root = try {
                JsonParser.parseString(body).asJsonObject
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse update JSON")
                throw AppUpdateException("更新接口返回格式错误", e)
            }
            return parser(root)
        }
    }

    private fun fallbackApkFileName(versionName: String, versionCode: Int): String {
        val suffix = versionName.ifBlank { versionCode.toString() }
        return "NEU-ECode-$suffix.apk"
    }

    private fun userAgent(): String = "NEU-eCode/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JsonObject.stringOrNull(name: String): String? = get(name)?.takeIf { !it.isJsonNull }?.asString
    private fun JsonObject.intOrNull(name: String): Int? = get(name)?.takeIf { !it.isJsonNull }?.asInt
    private fun JsonObject.longOrNull(name: String): Long? = get(name)?.takeIf { !it.isJsonNull }?.asLong
    private fun JsonObject.boolOrNull(name: String): Boolean? = get(name)?.takeIf { !it.isJsonNull }?.asBoolean

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
