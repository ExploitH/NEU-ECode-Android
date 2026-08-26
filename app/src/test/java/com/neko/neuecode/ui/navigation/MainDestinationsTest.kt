package com.neko.neuecode.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainDestinationsTest {

    @Test
    fun bottomBar_isExactlyPayScheduleMeInThatOrder() {
        assertEquals(
            listOf("pay", "schedule", "me"),
            MainDestinations.bottomBar,
        )
        assertEquals(3, MainDestinations.bottomBar.size)
    }

    @Test
    fun rechargeIntranetAndEcodeWebView_areSecondaryAndNotInBottomBar() {
        assertEquals(
            listOf("recharge", "intranet", "ecodeWebView"),
            MainDestinations.secondary,
        )
        assertFalse(MainDestinations.bottomBar.contains("recharge"))
        assertFalse(MainDestinations.bottomBar.contains("intranet"))
        assertFalse(MainDestinations.bottomBar.contains("ecodeWebView"))
        assertTrue(MainDestinations.isSecondary("recharge"))
        assertTrue(MainDestinations.isSecondary("intranet"))
        assertTrue(MainDestinations.isSecondary("ecodeWebView"))
        assertFalse(MainDestinations.isBottomBar("recharge"))
        assertFalse(MainDestinations.isBottomBar("intranet"))
        assertFalse(MainDestinations.isBottomBar("ecodeWebView"))
    }

    @Test
    fun payCodeFailureStart_navigatesToEcodeWebView() {
        assertEquals("ecodeWebView", MainDestinations.openPayCodeRoute)
        assertEquals(MainDestinations.ECODE_WEBVIEW, MainDestinations.openPayCodeRoute)
    }

    @Test
    fun ecodeWebView_isNotABottomBarDestination() {
        assertFalse(MainDestinations.isBottomBar(MainDestinations.ECODE_WEBVIEW))
        assertFalse(MainDestinations.bottomBar.contains(MainDestinations.ECODE_WEBVIEW))
        assertTrue(MainDestinations.isSecondary(MainDestinations.ECODE_WEBVIEW))
    }

    @Test
    fun bottomBarRoutes_areRecognizedAsBottomBarNotSecondary() {
        assertTrue(MainDestinations.isBottomBar(MainDestinations.PAY))
        assertTrue(MainDestinations.isBottomBar(MainDestinations.SCHEDULE))
        assertTrue(MainDestinations.isBottomBar(MainDestinations.ME))
        assertFalse(MainDestinations.isSecondary(MainDestinations.PAY))
        assertFalse(MainDestinations.isSecondary(MainDestinations.SCHEDULE))
        assertFalse(MainDestinations.isSecondary(MainDestinations.ME))
    }
}
