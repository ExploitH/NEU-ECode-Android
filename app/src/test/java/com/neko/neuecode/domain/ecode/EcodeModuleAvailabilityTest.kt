package com.neko.neuecode.domain.ecode

import com.neko.neuecode.ui.navigation.MainDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcodeModuleAvailabilityTest {

    @Test
    fun pause_disablesPayCodeFetchAndBackgroundWork() {
        assertFalse(EcodeModuleAvailability.ENABLED)
        assertFalse(EcodeModuleAvailability.shouldFetchPayCode())
        assertFalse(EcodeModuleAvailability.shouldScheduleEcodeBackgroundWork())
        assertFalse(EcodeModuleAvailability.shouldOpenPayCodeWebView())
        assertFalse(EcodeModuleAvailability.shouldOpenRecharge())
    }

    @Test
    fun pause_keepsScheduleAndIntranet() {
        assertTrue(EcodeModuleAvailability.shouldKeepSchedule())
        assertTrue(EcodeModuleAvailability.shouldKeepCampusVpn())
        assertEquals(MainDestinations.SCHEDULE, EcodeModuleAvailability.defaultStartRoute())
        assertEquals(MainDestinations.SCHEDULE, MainDestinations.resolveStartRoute(null))
        assertEquals(MainDestinations.SCHEDULE, MainDestinations.resolveStartRoute("pay"))
        assertEquals(MainDestinations.SCHEDULE, MainDestinations.resolveStartRoute("unexpected"))
    }

    @Test
    fun pause_noticeMatchesOwnerCopy() {
        assertEquals(
            "学校教务系统近期出现高频次更新，此模块需等待教务系统更新完毕后，再逐步开放。带来各种困扰，敬请谅解！",
            EcodeModuleAvailability.PAUSE_NOTICE,
        )
    }
}
