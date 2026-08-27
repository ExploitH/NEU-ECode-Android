package com.neko.neuecode.data.remote.ecode

import com.neko.neuecode.domain.ecode.PayCode
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parser for eCode pay-code bodies.
 *
 * Accepts both:
 * - Task 2 fixture envelope: `e` / `m` / `d.{payload,ttlSeconds,expiresAtEpochMs}`
 * - Live JSON:API: `data[0].attributes.{qrCode,qrInvalidTime}` (epoch-ms string)
 *
 * HTTP-layer failures belong to the repository. This parser only sees body text.
 * It does **not** decrypt RSA. If fixture `d` is a JSON object string it is
 * parsed; opaque ciphertext is [PayCodeFailure.ProtocolError].
 */
object ECodePayCodeParser {

    fun parse(json: String, nowEpochMs: Long = System.currentTimeMillis()): PayCodeParseResult {
        val trimmed = json.trim()
        if (looksLikeHtml(trimmed)) {
            return PayCodeParseResult.Failure(PayCodeFailure.NeedRelogin, "HTML login page")
        }

        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return PayCodeParseResult.Failure(PayCodeFailure.Unknown, "unparseable JSON")
        }

        if (root.has("data") && root.optJSONArray("data") != null) {
            return parseJsonApi(root, nowEpochMs)
        }

        val envelopeCode = envelopeCode(root)
        val envelopeMessage = root.optString("m", "")
        if (envelopeCode != "0") {
            return PayCodeParseResult.Failure(
                classifyError(envelopeCode, envelopeMessage),
                envelopeMessage.ifBlank { null },
            )
        }

        val data = resolveDataObject(root) ?: return PayCodeParseResult.Failure(
            PayCodeFailure.ProtocolError,
            "decryption is out of scope for this parser",
        )

        val payload = data.optString("payload", "").trim()
        if (payload.isEmpty()) {
            return PayCodeParseResult.Failure(PayCodeFailure.ProtocolError, "blank payload")
        }

        val hasTtl = data.has("ttlSeconds") && !data.isNull("ttlSeconds")
        val hasExpires = data.has("expiresAtEpochMs") && !data.isNull("expiresAtEpochMs")
        val ttlSecondsRaw = if (hasTtl) data.optInt("ttlSeconds") else null
        val expiresAtRaw = if (hasExpires) data.optLong("expiresAtEpochMs") else null

        val expiresAtEpochMs = when {
            expiresAtRaw != null -> expiresAtRaw
            ttlSecondsRaw != null -> nowEpochMs + ttlSecondsRaw.toLong() * 1000L
            else -> return PayCodeParseResult.Failure(
                PayCodeFailure.ProtocolError,
                "missing ttlSeconds and expiresAtEpochMs",
            )
        }
        val ttlSeconds = when {
            ttlSecondsRaw != null -> ttlSecondsRaw
            else -> ((expiresAtEpochMs - nowEpochMs) / 1000L).toInt().coerceAtLeast(0)
        }

        if (expiresAtEpochMs <= nowEpochMs) {
            return PayCodeParseResult.Failure(PayCodeFailure.Expired, envelopeMessage.ifBlank { null })
        }

        return PayCodeParseResult.Success(
            PayCode(
                payload = payload,
                expiresAtEpochMs = expiresAtEpochMs,
                ttlSeconds = ttlSeconds,
            ),
        )
    }

    private fun parseJsonApi(root: JSONObject, nowEpochMs: Long): PayCodeParseResult {
        val data = root.optJSONArray("data")
            ?: return PayCodeParseResult.Failure(PayCodeFailure.ProtocolError, "JSON:API data is not an array")
        if (data.length() == 0) {
            return PayCodeParseResult.Failure(PayCodeFailure.ProtocolError, "JSON:API data is empty")
        }
        val first = data.optJSONObject(0)
            ?: return PayCodeParseResult.Failure(PayCodeFailure.ProtocolError, "JSON:API data[0] missing")
        val attributes = first.optJSONObject("attributes")
            ?: return PayCodeParseResult.Failure(PayCodeFailure.ProtocolError, "JSON:API attributes missing")

        val qrCode = attributes.optString("qrCode", "").trim()
        if (qrCode.isEmpty()) {
            return PayCodeParseResult.Failure(PayCodeFailure.ProtocolError, "blank qrCode")
        }

        val expiresAtEpochMs = parseEpochMs(attributes, "qrInvalidTime")
            ?: return PayCodeParseResult.Failure(PayCodeFailure.ProtocolError, "invalid qrInvalidTime")
        val ttlSeconds = ((expiresAtEpochMs - nowEpochMs) / 1000L).toInt().coerceAtLeast(0)

        if (expiresAtEpochMs <= nowEpochMs) {
            return PayCodeParseResult.Failure(PayCodeFailure.Expired)
        }

        return PayCodeParseResult.Success(
            PayCode(
                payload = qrCode,
                expiresAtEpochMs = expiresAtEpochMs,
                ttlSeconds = ttlSeconds,
            ),
        )
    }

    private fun parseEpochMs(attributes: JSONObject, key: String): Long? {
        if (!attributes.has(key) || attributes.isNull(key)) return null
        val raw = attributes.opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toLong()
            else -> raw.toString().trim().toLongOrNull()
        }
    }

    private fun looksLikeHtml(body: String): Boolean {
        if (body.isEmpty()) return false
        val lowered = body.lowercase()
        return body.startsWith("<") ||
            lowered.startsWith("<!doctype") ||
            lowered.contains("<html")
    }

    private fun envelopeCode(root: JSONObject): String {
        if (!root.has("e") || root.isNull("e")) return "0"
        return root.opt("e")?.toString() ?: "0"
    }

    /**
     * Accepts `d` as a JSON object or as a JSON **string** of that object
     * (common NEU envelope). Opaque non-JSON ciphertext is not decrypted.
     */
    private fun resolveDataObject(root: JSONObject): JSONObject? {
        if (!root.has("d") || root.isNull("d")) return null
        return when (val raw = root.get("d")) {
            is JSONObject -> raw
            is String -> parseInnerJsonObject(raw)
            is JSONArray -> null
            else -> null
        }
    }

    private fun parseInnerJsonObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) {
            return null
        }
        return try {
            val parsed = JSONObject(trimmed)
            parsed
        } catch (_: JSONException) {
            null
        }
    }

    private fun classifyError(code: String, message: String): PayCodeFailure {
        val haystack = "$code $message"
        return when {
            containsAny(haystack, "未登录", "NEED_RELOGIN", "ticket") -> PayCodeFailure.NeedRelogin
            containsAny(haystack, "401", "403", "Unauthenticated") -> PayCodeFailure.Unauthenticated
            containsAny(haystack, "校园网", "NeedCampusNet", "campus") -> PayCodeFailure.NeedCampusNet
            containsAny(haystack, "过期", "Expired", "ttl") -> PayCodeFailure.Expired
            else -> PayCodeFailure.ProtocolError
        }
    }

    private fun containsAny(haystack: String, vararg needles: String): Boolean {
        return needles.any { haystack.contains(it, ignoreCase = true) }
    }
}
