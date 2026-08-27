package com.neko.neuecode.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandColorsTest {
    @Test
    fun mountainRiverBlue_matchesLauncherPack() {
        assertEquals(0xFF0F45AFL, BrandColors.MOUNTAIN_RIVER_BLUE_ARGB)
        assertEquals(0xFF0F45AF.toInt(), BrandColors.MountainRiverBlue.toArgb())
    }
}
