package com.neko.neuecode.data.remote.update

data class AppVersionInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val releaseNotes: String,
    val updateRequired: Boolean,
    val forceUpdate: Boolean
)

data class UpdateSession(
    val state: String,
    val verifyUrl: String,
    val expiresIn: Long
)

data class ApkDownloadInfo(
    val versionCode: Int,
    val versionName: String,
    val fileName: String,
    val sha256: String,
    val size: Long,
    val expiresIn: Long,
    val downloadUrl: String
)

class AppUpdateException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
