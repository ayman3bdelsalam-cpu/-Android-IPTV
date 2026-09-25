package com.ayman.smartiptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF060D1A)
val BgCard = Color(0xFF0D162B)
val BgCardAlt = Color(0xFF0F182E)
val AccentBlue = Color(0xFF38BDF8)
val AccentBlueDark = Color(0xFF0284C7)
val AccentRed = Color(0xFFF43F5E)
val AccentOrange = Color(0xFFF59E0B)
val TextMuted = Color(0xFF94A3B8)
val TextPrimary = Color(0xFFF8FAFC)

private val AppColorScheme = darkColorScheme(
    primary = AccentBlue,
    background = BgDark,
    surface = BgCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = AccentRed
)

@Composable
fun AymanIPTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
