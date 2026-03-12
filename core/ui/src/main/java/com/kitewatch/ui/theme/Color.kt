package com.kitewatch.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Light palette ─────────────────────────────────────────────────────────────
private val Blue60 = Color(0xFF1A73E8)
private val Green60 = Color(0xFF34A853)
private val Red60 = Color(0xFFEA4335)
private val ErrorRed = Color(0xFFD32F2F)
private val SurfaceLight = Color(0xFFFAFAFA)

val LightColorScheme =
    lightColorScheme(
        primary = Blue60,
        onPrimary = Color.White,
        secondary = Green60,
        onSecondary = Color.White,
        tertiary = Red60,
        onTertiary = Color.White,
        background = Color.White,
        onBackground = Color(0xFF1C1B1F),
        surface = SurfaceLight,
        onSurface = Color(0xFF1C1B1F),
        error = ErrorRed,
        onError = Color.White,
    )

// ── Dark palette ──────────────────────────────────────────────────────────────
private val Blue80 = Color(0xFF8AB4F8)
private val Green80 = Color(0xFF81C995)
private val Red80 = Color(0xFFF28B82)
private val ErrorRedDark = Color(0xFFCF6679)
private val SurfaceDark = Color(0xFF1E1E1E)
private val BackgroundDark = Color(0xFF121212)

val DarkColorScheme =
    darkColorScheme(
        primary = Blue80,
        onPrimary = Color(0xFF003258),
        secondary = Green80,
        onSecondary = Color(0xFF003919),
        tertiary = Red80,
        onTertiary = Color(0xFF5C0007),
        background = BackgroundDark,
        onBackground = Color(0xFFE6E1E5),
        surface = SurfaceDark,
        onSurface = Color(0xFFE6E1E5),
        error = ErrorRedDark,
        onError = Color(0xFF601410),
    )
