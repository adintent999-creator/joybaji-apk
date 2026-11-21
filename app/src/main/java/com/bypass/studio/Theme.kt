package com.bypass.studio

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LocalDensity

enum class StudioTheme(val label: String) {
    OBSIDIAN("Midnight Obsidian"),
    GRAPHITE("Graphite Pro"),
    SOLAR("Solar Terminal")
}

data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val green: Color
)

fun paletteFor(theme: StudioTheme): Palette = when (theme) {
    StudioTheme.OBSIDIAN -> Palette(
        bg = Color(0xFF06070A), surface = Color(0xFF0B0D12), surface2 = Color(0xFF11141B),
        accent = Color(0xFF00D9F5), text = Color(0xFFEAF6F8), muted = Color(0xFF7E8B96),
        border = Color(0xFF1E2730), green = Color(0xFF39D98A)
    )
    StudioTheme.GRAPHITE -> Palette(
        bg = Color(0xFF0A0A0C), surface = Color(0xFF121216), surface2 = Color(0xFF19191F),
        accent = Color(0xFFB57BFF), text = Color(0xFFF1ECF8), muted = Color(0xFF91899C),
        border = Color(0xFF282330), green = Color(0xFF54D69B)
    )
    StudioTheme.SOLAR -> Palette(
        bg = Color(0xFF0C0906), surface = Color(0xFF16110A), surface2 = Color(0xFF1F170D),
        accent = Color(0xFFFFB020), text = Color(0xFFFFF3DE), muted = Color(0xFFA89172),
        border = Color(0xFF3A2B18), green = Color(0xFF7BD88F)
    )
}

@Composable
fun StudioTheme(theme: StudioTheme, content: @Composable () -> Unit) {
    val p = paletteFor(theme)
    val currentDensity = LocalDensity.current
    val scale = themeScale(theme)
    val scheme = darkColorScheme(
        primary = p.accent,
        onPrimary = Color.Black,
        primaryContainer = p.surface2,
        onPrimaryContainer = p.text,
        secondary = p.green,
        onSecondary = Color.Black,
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.muted,
        outline = p.border,
        error = Color(0xFFFF6B6B)
    )
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density * scale,
            fontScale = currentDensity.fontScale * scale
        )
    ) {
        MaterialTheme(colorScheme = scheme, typography = studioTypography(theme), content = content)
    }
}
