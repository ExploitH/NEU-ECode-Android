package com.neko.neuecode.domain.jwxt

import org.junit.Assert.assertEquals
import org.junit.Test

class JwxtTermNamesTest {

    @Test
    fun displayName_mapsAutumnAndSpringCodes() {
        assertEquals("2026-2027学年秋季学期", JwxtTermNames.displayName("2026-2027-1"))
        assertEquals("2025-2026学年春季学期", JwxtTermNames.displayName("2025-2026-2"))
    }

    @Test
    fun displayName_keepsChineseNameFromJwxt() {
        assertEquals(
            "2026-2027学年秋季学期",
            JwxtTermNames.displayName("2026-2027-1", "2026-2027学年秋季学期"),
        )
    }

    @Test
    fun displayName_replacesCodeCopiedAsName() {
        assertEquals(
            "2026-2027学年秋季学期",
            JwxtTermNames.displayName("2026-2027-1", "2026-2027-1"),
        )
        assertEquals(
            "2026-2027学年秋季学期",
            JwxtNamedCode("2026-2027-1", "2026-2027-1").displayName,
        )
    }
}
