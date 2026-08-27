package com.neko.neuecode.data.local.cookie

object CookieMerge {
    fun keyOf(cookie: SerializableCookie): String {
        return "${cookie.domain}\u0000${cookie.path}\u0000${cookie.name}"
    }

    fun merge(
        existing: Collection<SerializableCookie>,
        loaded: Collection<SerializableCookie>,
    ): List<SerializableCookie> {
        val merged = LinkedHashMap<String, SerializableCookie>()
        loaded.forEach { cookie ->
            merged[keyOf(cookie)] = cookie
        }
        existing.forEach { live ->
            val key = keyOf(live)
            val disk = merged[key]
            if (disk == null || live.value.isNotBlank()) {
                merged[key] = live
            }
        }
        return merged.values.toList()
    }
}
