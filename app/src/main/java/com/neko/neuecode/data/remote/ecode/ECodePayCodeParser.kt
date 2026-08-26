package com.neko.neuecode.data.remote.ecode

import com.neko.neuecode.domain.ecode.PayCode
import com.neko.neuecode.domain.ecode.PayCodeFailure
import com.neko.neuecode.domain.ecode.PayCodeParseResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Replaceable fixture parser for eCode pay-code envelopes.
 *
 * Provisional JSON field names (Task 3 may retarget these without changing
 * [PayCode] / [PayCodeFailure]):
 * - envelope: `e` (success when `"0"`), `m` (message), `d` (data object or JSON string)
 * - inner object: `payload`, `ttlSeconds`, `expiresAtEpochMs`
 *
 * This parser does **not** decrypt RSA. If `d` is a JSON object string it is
 * parsed; opaque ciphertext is [PayCodeFailure.ProtocolError].
 */
object ECodePayCodeParser {

    fun parse(json: String, nowEpochMs: Long = System.currentTimeMillis()): PayCodeParseResult {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return PayCodeParseResult.Failure(PayCodeFailure.Unknown, "unparseable JSON")
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
