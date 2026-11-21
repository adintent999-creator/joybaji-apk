package com.bypass.studio

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val bengaliFont = FontFamily(Font(R.font.noto_sans_bengali))
val uiFont = FontFamily(Font(R.font.inter))
val monoFont = FontFamily(Font(R.font.jetbrains_mono))

fun themeScale(theme: StudioTheme): Float = when (theme) {
    StudioTheme.OBSIDIAN -> 1.0f
    StudioTheme.GRAPHITE -> 0.92f
    StudioTheme.SOLAR -> 1.05f
}

fun studioTypography(theme: StudioTheme): Typography {
    val s = themeScale(theme)
    fun sz(value: Int) = (value * s).sp
    fun lh(value: Int) = (value * s).sp
    return Typography(
        bodyLarge = TextStyle(fontFamily = bengaliFont, fontSize = sz(16), lineHeight = lh(26), letterSpacing = 0.sp),
        bodyMedium = TextStyle(fontFamily = bengaliFont, fontSize = sz(14), lineHeight = lh(23), letterSpacing = 0.sp),
        bodySmall = TextStyle(fontFamily = bengaliFont, fontSize = sz(12), lineHeight = lh(20), letterSpacing = 0.sp),
        headlineSmall = TextStyle(
            fontFamily = bengaliFont,
            fontWeight = FontWeight.Bold,
            fontSize = sz(22),
            lineHeight = lh(34),
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = uiFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = sz(16),
            lineHeight = lh(25),
            letterSpacing = 0.sp
        ),
        labelLarge = TextStyle(
            fontFamily = uiFont,
            fontWeight = FontWeight.Medium,
            fontSize = sz(14),
            lineHeight = lh(22),
            letterSpacing = 0.sp
        )
    )
}
