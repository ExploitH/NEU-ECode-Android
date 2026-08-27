package com.neko.neuecode.data.local.cookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieMergeTest {

    @Test
    fun restoreMustNotWipeLiveSessionCookie() {
        val live = SerializableCookie(
            name = "SESSION",
            value = "fresh-jwxt-session",
            domain = "jwxt.neu.edu.cn",
            path = "/",
            hostOnly = true,
        )
        val stale = SerializableCookie(
            name = "SESSION",
            value = "stale-or-empty",
            domain = "jwxt.neu.edu.cn",
            path = "/",
            hostOnly = true,
        )
        val merged = CookieMerge.merge(
            existing = listOf(live),
            loaded = listOf(stale),
        )
        assertEquals("fresh-jwxt-session", merged.single { it.name == "SESSION" }.value)
    }

    @Test
    fun restoreKeepsUnrelatedDiskCookies() {
        val live = SerializableCookie(name = "SESSION", value = "live", domain = "jwxt.neu.edu.cn")
        val disk = SerializableCookie(name = "CASTGC", value = "tgt", domain = "pass.neu.edu.cn")
        val merged = CookieMerge.merge(existing = listOf(live), loaded = listOf(disk))
        assertTrue(merged.any { it.name == "SESSION" && it.value == "live" })
        assertTrue(merged.any { it.name == "CASTGC" && it.value == "tgt" })
    }
}
