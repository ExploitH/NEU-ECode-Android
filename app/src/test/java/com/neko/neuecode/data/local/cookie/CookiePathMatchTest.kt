package com.neko.neuecode.data.local.cookie

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiePathMatchTest {

    @Test
    fun moduleSessionCookie_doesNotMatchKbappUrl() {
        val root = cookie("/", "root-session")
        val module = cookie("/jwapp/sys/jwpubapp/", "jwpubapp-session")
        val kbapp = "https://jwxt.neu.edu.cn/jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do".toHttpUrl()
        val pub = "https://jwxt.neu.edu.cn/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do".toHttpUrl()

        assertTrue(root.matches(kbapp))
        assertFalse(module.matches(kbapp))
        assertTrue(root.matches(pub))
        assertTrue(module.matches(pub))
    }

    private fun cookie(path: String, value: String): Cookie {
        return Cookie.Builder()
            .name("SESSION")
            .value(value)
            .hostOnlyDomain("jwxt.neu.edu.cn")
            .path(path)
            .build()
    }
}
