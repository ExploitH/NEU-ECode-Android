package com.neko.neuecode.domain.ecode

import com.neko.neuecode.ui.navigation.MainDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcodeModuleAvailabilityTest {

    @Test
    fun enabled_allowsPayCodeFetchAndBackgroundWork() {
        assertTrue(EcodeModuleAvailability.ENABLED)
        assertTrue(EcodeModuleAvailability.shouldFetchPayCode())
        assertTrue(EcodeModuleAvailability.shouldScheduleEcodeBackgroundWork())
        assertTrue(EcodeModuleAvailability.shouldOpenPayCodeWebView())
        assertTrue(EcodeModuleAvailability.shouldOpenRecharge())
    }

    @Test
    fun enabled_keepsScheduleIntranetAndPaysAsDefaultStart() {
        assertTrue(EcodeModuleAvailability.shouldKeepSchedule())
        assertTrue(EcodeModuleAvailability.shouldKeepCampusVpn())
        assertEquals(MainDestinations.PAY, EcodeModuleAvailability.defaultStartRoute())
        assertEquals(MainDestinations.PAY, MainDestinations.resolveStartRoute(null))
        assertEquals(MainDestinations.PAY, MainDestinations.resolveStartRoute("pay"))
        assertEquals(MainDestinations.PAY, MainDestinations.resolveStartRoute("unexpected"))
        assertEquals(MainDestinations.SCHEDULE, MainDestinations.resolveStartRoute("schedule"))
    }

    @Test
    fun pause_noticeMatchesOwnerCopy() {
        assertEquals(
            "学校教务系统近期出现高频次更新，此模块需等待教务系统更新完毕后，再逐步开放。带来各种困扰，敬请谅解！",
            EcodeModuleAvailability.PAUSE_NOTICE,
        )
    }
}
