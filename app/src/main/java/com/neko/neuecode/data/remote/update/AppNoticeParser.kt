package com.neko.neuecode.data.remote.update

import com.google.gson.JsonObject
import com.neko.neuecode.domain.notice.AppNotice
import com.neko.neuecode.domain.notice.NoticePriority
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object AppNoticeParser {
    fun parseNotices(root: JsonObject, nowMillis: Long = System.currentTimeMillis()): List<AppNotice> {
        val element = root.get("notices") ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        val array = element.asJsonArray
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val raw = item.asJsonObject
            val id = raw.stringOrEmpty("id")
            val title = raw.stringOrEmpty("title")
            val body = raw.stringOrEmpty("body").ifBlank { raw.stringOrEmpty("content") }
            if (id.isBlank() || title.isBlank() || body.isBlank()) return@mapNotNull null
            val expiresAt = raw.stringOrEmpty("expires_at").ifBlank { raw.stringOrEmpty("expiresAt") }
            if (isExpired(expiresAt, nowMillis)) return@mapNotNull null
            AppNotice(
                id = id,
                title = title,
                body = body,
                priority = NoticePriority.fromRaw(raw.stringOrEmpty("priority")),
                publishedAt = raw.stringOrEmpty("published_at").ifBlank { raw.stringOrEmpty("publishedAt") },
                expiresAt = expiresAt,
            )
        }
    }

    private fun isExpired(expiresAt: String, nowMillis: Long): Boolean {
        val expiresMillis = parseIsoMillis(expiresAt) ?: return false
        return expiresMillis <= nowMillis
    }

    private fun parseIsoMillis(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(text)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun JsonObject.stringOrEmpty(name: String): String {
        val value = get(name) ?: return ""
        if (value.isJsonNull) return ""
        return try {
            value.asString.trim()
        } catch (_: Exception) {
            value.toString().trim('"', ' ')
        }
    }
}
