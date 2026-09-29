package com.neko.neuecode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.neko.neuecode.domain.jwxt.CourseColorHasher

data class CourseTone(val container: Color, val content: Color)

/**
 * Curated course colours. Light containers match the widget card drawables
 * (`schedule_day_class_card_bg_N`), so a course keeps its colour everywhere.
 */
object CoursePalette {
    private val light = listOf(
        CourseTone(Color(0xFFCFE8FF), Color(0xFF0B4A7A)),
        CourseTone(Color(0xFFD6E4FF), Color(0xFF1E3A8A)),
        CourseTone(Color(0xFFE1D7FF), Color(0xFF4C2A99)),
        CourseTone(Color(0xFFFFD8E4), Color(0xFF8A1F4A)),
        CourseTone(Color(0xFFFFE0C2), Color(0xFF8A4200)),
        CourseTone(Color(0xFFD7F3D4), Color(0xFF1F5E24)),
        CourseTone(Color(0xFFCCF0EC), Color(0xFF0F5C55)),
        CourseTone(Color(0xFFFFEDB8), Color(0xFF7A5200)),
        CourseTone(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
        CourseTone(Color(0xFFE0E4F0), Color(0xFF38425C)),
    )

    private val dark = listOf(
        CourseTone(Color(0xFF16374F), Color(0xFFCFE8FF)),
        CourseTone(Color(0xFF1F2F5C), Color(0xFFD6E4FF)),
        CourseTone(Color(0xFF33285E), Color(0xFFE1D7FF)),
        CourseTone(Color(0xFF52222F), Color(0xFFFFD8E4)),
        CourseTone(Color(0xFF52341A), Color(0xFFFFE0C2)),
        CourseTone(Color(0xFF1F4224), Color(0xFFD7F3D4)),
        CourseTone(Color(0xFF15413D), Color(0xFFCCF0EC)),
        CourseTone(Color(0xFF4D3B12), Color(0xFFFFEDB8)),
        CourseTone(Color(0xFF55201C), Color(0xFFFFDAD6)),
        CourseTone(Color(0xFF2B3142), Color(0xFFE0E4F0)),
    )

    @Composable
    @ReadOnlyComposable
    fun tone(courseKey: String): CourseTone {
        val tones = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) dark else light
        return tones[CourseColorHasher.paletteIndex(courseKey)]
    }
}
