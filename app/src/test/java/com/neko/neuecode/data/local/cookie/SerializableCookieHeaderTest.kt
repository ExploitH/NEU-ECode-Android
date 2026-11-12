package com.neko.neuecode.data.local.cookie

import org.junit.Assert.*
import org.junit.Test

class SerializableCookieHeaderTest {
    @Test fun preservesHttpOnlyAndNarrowPathWithoutNewPlatformApis() {
        val cookie = SerializableCookie.fromSetCookieString("https://pass.neu.edu.cn/tpass/login", "JSESSIONID=fixture; Path=/tpass; Secure; hTtPoNlY")!!
        assertTrue(cookie.httpOnly)
        assertTrue(cookie.secure)
        assertEquals("/tpass", cookie.path)
        assertTrue(cookie.toOkHttpCookie()!!.httpOnly)
    }

    @Test fun doesNotMistakeCookieValueOrAttributeValueForHttpOnlyFlag() {
        assertFalse(SerializableCookie.fromSetCookieString("https://pay.neu.edu.cn/", "value=HttpOnly; Path=/")!!.httpOnly)
        assertFalse(SerializableCookie.fromSetCookieString("https://pay.neu.edu.cn/", "value=fixture; Path=/; HttpOnly=no")!!.httpOnly)
    }
}
