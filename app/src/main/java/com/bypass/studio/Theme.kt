package com.bypass.studio

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

val bengaliFont = FontFamily(Font(R.font.noto_sans_bengali))
val monoFont = FontFamily(Font(R.font.jetbrains_mono))

@Composable
fun StudioTheme(theme: StudioTheme, content: @Composable () -> Unit) {
    val p = paletteFor(theme)
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
    val type = Typography(
        bodyLarge = TextStyle(fontFamily = bengaliFont, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
        bodyMedium = TextStyle(fontFamily = bengaliFont, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodySmall = TextStyle(fontFamily = bengaliFont, fontSize = 12.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = bengaliFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 33.sp, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = bengaliFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        labelLarge = TextStyle(fontFamily = bengaliFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp)
    )
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
