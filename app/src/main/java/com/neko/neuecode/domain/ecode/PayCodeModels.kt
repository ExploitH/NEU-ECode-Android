package com.neko.neuecode.domain.ecode

/**
 * Native pay-code payload for later QR encoding.
 *
 * [payload] is the string a widget/screen would encode — not an Android Bitmap.
 */
data class PayCode(
    val payload: String,
    val expiresAtEpochMs: Long,
    val ttlSeconds: Int,
)

enum class PayCodeFailure {
    Expired,
    Unauthenticated,
    NeedCampusNet,
    NeedRelogin,
    NeedSms,
    ProtocolError,
    Unknown,
}

sealed class PayCodeParseResult {
    data class Success(val code: PayCode) : PayCodeParseResult()
    data class Failure(val reason: PayCodeFailure, val message: String? = null) : PayCodeParseResult()
}
