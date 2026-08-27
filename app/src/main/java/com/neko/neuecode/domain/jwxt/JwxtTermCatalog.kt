package com.neko.neuecode.domain.jwxt

object JwxtTermCatalog {
    fun recent(
        terms: List<JwxtNamedCode>,
        currentCode: String?,
        limit: Int = 8,
    ): List<JwxtNamedCode> {
        if (terms.isEmpty() || limit <= 0) return emptyList()
        val unique = terms.distinctBy { it.code }.sortedBy { it.code }
        val currentIndex = unique.indexOfFirst { it.code == currentCode }.let { if (it < 0) unique.lastIndex else it }
        val half = (limit - 1) / 2
        var start = (currentIndex - half).coerceAtLeast(0)
        val endExclusive = (start + limit).coerceAtMost(unique.size)
        start = (endExclusive - limit).coerceAtLeast(0)
        return unique.subList(start, endExclusive)
    }
}
