package com.neko.neuecode.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ECodeWidgetPresentationTest {

    @Test
    fun balancesVisibleOnlyWhenSettingEnabled() {
        assertTrue(ECodeWidgetPresentation.showBalances(showBalance = true))
        assertFalse(ECodeWidgetPresentation.showBalances(showBalance = false))
    }

    @Test
    fun qrStatus_doesNotAskToOpenApp() {
        val ready = ECodeWidgetPresentation.qrStatus(success = true, ttlSeconds = 15)
        val failed = ECodeWidgetPresentation.qrStatus(success = false, ttlSeconds = null)
        assertFalse(ready.contains("打开 App"))
        assertFalse(failed.contains("打开 App"))
        assertTrue(ready.contains("付款码") || ready.contains("秒"))
    }

    @Test
    fun actions_bodyRefreshesQr_buttonRefreshesBalance() {
        assertEquals(ECodeWidgetPresentation.ACTION_REFRESH_QR, ECodeWidgetPresentation.bodyClickAction())
        assertEquals(ECodeWidgetPresentation.ACTION_REFRESH_BALANCE, ECodeWidgetPresentation.refreshClickAction())
    }
}
