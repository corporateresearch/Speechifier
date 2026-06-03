package com.speechifier.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark palette mirroring the web app (backend/static/styles.css): deep near-black
 * background, violet accent, and a warm highlight used for the active word.
 */
object SpeechifierColors {
    val Background = Color(0xFF0B0B12)
    val Surface = Color(0xFF14141F)
    val SurfaceVariant = Color(0xFF1C1C2B)
    val Accent = Color(0xFF8B7BFF)
    val OnAccent = Color(0xFF0B0B12)
    val TextPrimary = Color(0xFFEDEDF5)
    val TextMuted = Color(0xFF9A9AB2)

    /** Active-word highlight (background + text). */
    val HighlightBg = Color(0xFF8B7BFF)
    val HighlightText = Color(0xFF0B0B12)

    /** Sentence currently being read (subtler than the active word). */
    val ActiveSentenceBg = Color(0x1A8B7BFF)
}

private val DarkColors = darkColorScheme(
    primary = SpeechifierColors.Accent,
    onPrimary = SpeechifierColors.OnAccent,
    background = SpeechifierColors.Background,
    onBackground = SpeechifierColors.TextPrimary,
    surface = SpeechifierColors.Surface,
    onSurface = SpeechifierColors.TextPrimary,
    surfaceVariant = SpeechifierColors.SurfaceVariant,
    onSurfaceVariant = SpeechifierColors.TextMuted,
)

@Composable
fun SpeechifierTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The app is dark-only by design, matching the original glassmorphism UI.
    MaterialTheme(colorScheme = DarkColors, content = content)
}
