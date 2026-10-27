package com.neko.neuecode.domain.notice

enum class NoticePriority {
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {
        fun fromRaw(raw: String?): NoticePriority {
            return when (raw?.trim()?.lowercase()) {
                "low" -> LOW
                "high" -> HIGH
                else -> MEDIUM
            }
        }
    }
}

data class AppNotice(
    val id: String,
    val title: String,
    val body: String,
    val priority: NoticePriority,
    val publishedAt: String = "",
    val expiresAt: String = "",
)

object NoticeInbox {
    fun unread(notices: List<AppNotice>, readIds: Set<String>): List<AppNotice> {
        return notices.filter { it.id.isNotBlank() && it.id !in readIds }
    }

    fun showBadge(notices: List<AppNotice>, readIds: Set<String>): Boolean {
        return unread(notices, readIds).any { it.priority != NoticePriority.LOW }
    }

    fun splashNotices(notices: List<AppNotice>, readIds: Set<String>): List<AppNotice> {
        return unread(notices, readIds)
            .filter { it.priority == NoticePriority.HIGH }
            .sortedWith(compareByDescending<AppNotice> { it.publishedAt }.thenBy { it.id })
    }

    fun markRead(@Suppress("UNUSED_PARAMETER") notices: List<AppNotice>, readIds: Set<String>, noticeId: String): Set<String> {
        if (noticeId.isBlank()) return readIds
        return readIds + noticeId
    }

    fun markAllRead(notices: List<AppNotice>, readIds: Set<String>): Set<String> {
        return readIds + notices.map { it.id }.filter { it.isNotBlank() }
    }
}
