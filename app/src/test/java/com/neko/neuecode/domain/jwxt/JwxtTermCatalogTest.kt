package com.neko.neuecode.domain.jwxt

import org.junit.Assert.assertEquals
import org.junit.Test

class JwxtTermCatalogTest {

    @Test
    fun recent_keepsCurrentTermAndNearbyCodes() {
        val terms = listOf(
            JwxtNamedCode("2023-2024-2", "2023-2024学年春季学期"),
            JwxtNamedCode("2024-2025-1", "2024-2025学年秋季学期"),
            JwxtNamedCode("2024-2025-2", "2024-2025学年春季学期"),
            JwxtNamedCode("2025-2026-1", "2025-2026学年秋季学期"),
            JwxtNamedCode("2025-2026-2", "2025-2026学年春季学期"),
            JwxtNamedCode("2026-2027-1", "2026-2027学年秋季学期"),
        )

        val recent = JwxtTermCatalog.recent(
            terms = terms,
            currentCode = "2025-2026-2",
            limit = 4,
        )

        assertEquals(
            listOf("2024-2025-2", "2025-2026-1", "2025-2026-2", "2026-2027-1"),
            recent.map { it.code },
        )
    }
}
