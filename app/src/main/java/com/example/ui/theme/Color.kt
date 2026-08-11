package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Spotify-style Lyric Canvas Palette
val BackgroundDark = Color(0xFF090611)
val BackgroundSurface = Color(0xFF130E22)
val CardSurface = Color(0xFF1E1735)
val CardSurfaceElevated = Color(0xFF2B214A)

// Vibrant Neon Accents
val NeonPurple = Color(0xFFA855F7)
val NeonPurpleLight = Color(0xFFC084FC)
val NeonPink = Color(0xFFF43F5E)
val NeonCyan = Color(0xFF06B6D4)
val NeonEmerald = Color(0xFF10B981)
val NeonAmber = Color(0xFFF59E0B)

// Lyric State Colors
val ActiveWordText = Color(0xFFFFFFFF)
val ActiveWordBackground = Color(0xFF7C3AED)
val ActiveWordGlow = Color(0xFFD8B4FE)
val PastSentenceText = Color(0xFF94A3B8)
val FutureSentenceText = Color(0xFF475569)

// Theme Presets for Lyrics View
enum class LyricThemePreset(
    val title: String,
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val activeText: Color,
    val activePill: Color,
    val textMuted: Color
) {
    SPOTIFY_DARK(
        title = "Spotify Midnight",
        backgroundStart = Color(0xFF0F0B1E),
        backgroundEnd = Color(0xFF05030A),
        activeText = Color(0xFFFFFFFF),
        activePill = Color(0xFF8B5CF6),
        textMuted = Color(0xFF64748B)
    ),
    MIDNIGHT_PURPLE(
        title = "Neon Velvet",
        backgroundStart = Color(0xFF2E1065),
        backgroundEnd = Color(0xFF0F0728),
        activeText = Color(0xFFFFFFFF),
        activePill = Color(0xFFEC4899),
        textMuted = Color(0xFF94A3B8)
    ),
    PAPER_PARCHMENT(
        title = "Warm Parchment",
        backgroundStart = Color(0xFFFFFBEB),
        backgroundEnd = Color(0xFFFEF3C7),
        activeText = Color(0xFF1E1B4B),
        activePill = Color(0xFFD97706),
        textMuted = Color(0xFF78350F)
    ),
    CYBER_NEON(
        title = "Cyber Matrix",
        backgroundStart = Color(0xFF022C22),
        backgroundEnd = Color(0xFF064E3B),
        activeText = Color(0xFFECFDF5),
        activePill = Color(0xFF10B981),
        textMuted = Color(0xFF047857)
    ),
    OLED_BLACK(
        title = "Pitch OLED",
        backgroundStart = Color(0xFF000000),
        backgroundEnd = Color(0xFF000000),
        activeText = Color(0xFFFFFFFF),
        activePill = Color(0xFF3B82F6),
        textMuted = Color(0xFF525252)
    )
}
