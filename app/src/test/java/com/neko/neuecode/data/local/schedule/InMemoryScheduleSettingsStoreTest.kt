package com.neko.neuecode.data.local.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryScheduleSettingsStoreTest {

    @Test
    fun save_roundTripsDefaultTermAndStartDay() {
        val store = InMemoryScheduleSettingsStore()
        store.save(
            ScheduleSettings(
                defaultTermCode = "2025-2026-2",
                termStartEpochDay = 20_150L,
            ),
        )

        val loaded = store.load()
        assertEquals("2025-2026-2", loaded.defaultTermCode)
        assertEquals(20_150L, loaded.termStartEpochDay)
    }

    @Test
    fun load_defaultsToEmpty() {
        val store = InMemoryScheduleSettingsStore()
        val loaded = store.load()
        assertNull(loaded.defaultTermCode)
        assertNull(loaded.termStartEpochDay)
    }
}
