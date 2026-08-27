package com.neko.neuecode.data.remote.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceImeiTest {

    @Test
    fun rejectsBlankAndAllZeros() {
        assertNull(DeviceImei.normalize(null))
        assertNull(DeviceImei.normalize(""))
        assertNull(DeviceImei.normalize("00000000000000000000000000000000"))
    }

    @Test
    fun stripsUuidDashes() {
        assertEquals(
            "c0ffee00c0ffee00c0ffee00c0ffee00",
            DeviceImei.normalize("C0FFEE00-C0FF-EE00-C0FF-EE00C0FFEE00"),
        )
    }

    @Test
    fun newInstallId_is32HexAndNotAllZeros() {
        val id = DeviceImei.newInstallId()
        assertEquals(32, id.length)
        assertTrue(id.matches(Regex("^[0-9a-f]{32}$")))
        assertTrue(id.any { it != '0' })
    }
}
