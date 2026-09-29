package com.neko.neuecode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * Every role is spelled out on purpose: roles left unset fall back to the
 * Material 3 baseline purple, which clashes with the Mountain & River blue.
 * Neutrals follow the slate ramp already used by the home-screen widgets.
 */
private val LightColors = lightColorScheme(
    primary = BrandColors.MountainRiverBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A2A6B),
    inversePrimary = Color(0xFFA9C3FF),
    secondary = Color(0xFF4A5D82),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F5),
    onSecondaryContainer = Color(0xFF1B2A44),
    tertiary = Color(0xFF0E7C86),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCF0F2),
    onTertiaryContainer = Color(0xFF053B40),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF5F7FA),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF526075),
    surfaceTint = BrandColors.MountainRiverBlue,
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A1A12),
    outline = Color(0xFF8391A7),
    outlineVariant = Color(0xFFD5DCE6),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF5F7FA),
    surfaceDim = Color(0xFFD9DEE6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3F8),
    surfaceContainer = Color(0xFFEBEFF5),
    surfaceContainerHigh = Color(0xFFE5EAF1),
    surfaceContainerHighest = Color(0xFFDFE5EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C3FF),
    onPrimary = Color(0xFF0A2A6B),
    primaryContainer = Color(0xFF1C3F8C),
    onPrimaryContainer = Color(0xFFDCE6FF),
    inversePrimary = BrandColors.MountainRiverBlue,
    secondary = Color(0xFFB7C4E0),
    onSecondary = Color(0xFF20304D),
    secondaryContainer = Color(0xFF34425E),
    onSecondaryContainer = Color(0xFFE2E8F5),
    tertiary = Color(0xFF7FD6DE),
    onTertiary = Color(0xFF003A3F),
    tertiaryContainer = Color(0xFF0B4F56),
    onTertiaryContainer = Color(0xFFCCF0F2),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E7EE),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE3E7EE),
    surfaceVariant = Color(0xFF2A303A),
    onSurfaceVariant = Color(0xFFA7B0BE),
    surfaceTint = Color(0xFFA9C3FF),
    inverseSurface = Color(0xFFE3E7EE),
    inverseOnSurface = Color(0xFF1B2029),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0E08),
    errorContainer = Color(0xFF6B1B14),
    onErrorContainer = Color(0xFFFEE4E2),
    outline = Color(0xFF6B7482),
    outlineVariant = Color(0xFF363D48),
    scrim = Color.Black,
    surfaceBright = Color(0xFF343A44),
    surfaceDim = Color(0xFF0E1116),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF161A21),
    surfaceContainer = Color(0xFF1A1F27),
    surfaceContainerHigh = Color(0xFF212730),
    surfaceContainerHighest = Color(0xFF2B313B),
)

@Composable
fun NeuECodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

/**
 * Raised panel on the page background: white in light mode, one step above
 * the page in dark mode. Use for cards and grouped lists.
 */
val ColorScheme.panel: Color
    get() = if (background.luminance() < 0.5f) surfaceContainer else surfaceContainerLowest

@Composable
fun panelCardColors(): CardColors =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.panel)
