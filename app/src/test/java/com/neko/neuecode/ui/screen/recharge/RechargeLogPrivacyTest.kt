package com.neko.neuecode.ui.screen.recharge

import org.junit.Assert.*
import org.junit.Test

class RechargeLogPrivacyTest {
    @Test fun callbackAndWebflowSecretsAreRedacted() {
        val redacted = redactRechargeLog("https://pay.neu.edu.cn/openPortal;jsessionid=private-session?ticket=ST-private&execution=e2s1&lt=LT-private")
        assertFalse(redacted.contains("private"))
        assertFalse(redacted.contains("e2s1"))
        assertTrue(redacted.contains("/openPortal"))
    }
}
