package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Modern White-Dominated & Playful Yellow/Black Palette (Apple Minimalist Aesthetic)
val PureWhite = Color(0xFFFFFFFF)
val OffWhiteSurface = Color(0xFFF8FAFC)
val CardWhite = Color(0xFFFFFFFF)
val CardBorderSoft = Color(0xFFF1F5F9)

val PlayfulYellow = Color(0xFFFACC15)
val PlayfulYellowLight = Color(0xFFFEF08A)
val PlayfulYellowDark = Color(0xFFEAB308)

val CharcoalBlack = Color(0xFF111827)
val CharcoalSoft = Color(0xFF1F2937)

val TextMain = Color(0xFF0F172A)
val TextMuted = Color(0xFF64748B)
val TextSubtle = Color(0xFF94A3B8)

// Legacy Dark Compatibility Tokens
val BackgroundDark = Color(0xFF0F172A)
val BackgroundSurface = Color(0xFF1E293B)
val CardSurface = Color(0xFFFFFFFF)
val CardSurfaceElevated = Color(0xFFF8FAFC)

val NeonPurple = Color(0xFFFACC15)
val NeonPurpleLight = Color(0xFFFEF08A)
val NeonPink = Color(0xFF111827)
val NeonCyan = Color(0xFF0EA5E9)
val NeonEmerald = Color(0xFF10B981)
val NeonAmber = Color(0xFFF59E0B)

// Lyric State Colors
val ActiveWordText = Color(0xFF0F172A)
val ActiveWordBackground = Color(0xFFFACC15)
val ActiveWordGlow = Color(0xFFFEF08A)
val PastSentenceText = Color(0xFF64748B)
val FutureSentenceText = Color(0xFF94A3B8)

// Theme Presets for Lyrics View
enum class LyricThemePreset(
    val title: String,
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val activeText: Color,
    val activePill: Color,
    val textMuted: Color
) {
    SUNSHINE_WHITE(
        title = "Playful White",
        backgroundStart = Color(0xFFFFFFFF),
        backgroundEnd = Color(0xFFF8FAFC),
        activeText = Color(0xFF0F172A),
        activePill = Color(0xFFFACC15),
        textMuted = Color(0xFF64748B)
    ),
    SPOTIFY_DARK(
        title = "Playful White",
        backgroundStart = Color(0xFFFFFFFF),
        backgroundEnd = Color(0xFFF8FAFC),
        activeText = Color(0xFF0F172A),
        activePill = Color(0xFFFACC15),
        textMuted = Color(0xFF64748B)
    ),
    MIDNIGHT_PURPLE(
        title = "Clean Charcoal",
        backgroundStart = Color(0xFF111827),
        backgroundEnd = Color(0xFF1F2937),
        activeText = Color(0xFFFFFFFF),
        activePill = Color(0xFFFACC15),
        textMuted = Color(0xFF94A3B8)
    ),
    PAPER_PARCHMENT(
        title = "Warm Parchment",
        backgroundStart = Color(0xFFFFFBEB),
        backgroundEnd = Color(0xFFFEF3C7),
        activeText = Color(0xFF1E1B4B),
        activePill = Color(0xFFFACC15),
        textMuted = Color(0xFF78350F)
    ),
    CYBER_NEON(
        title = "Emerald Clean",
        backgroundStart = Color(0xFFF0FDF4),
        backgroundEnd = Color(0xFFDCFCE7),
        activeText = Color(0xFF14532D),
        activePill = Color(0xFFFACC15),
        textMuted = Color(0xFF166534)
    ),
    OLED_BLACK(
        title = "Minimal Pitch",
        backgroundStart = Color(0xFF000000),
        backgroundEnd = Color(0xFF111827),
        activeText = Color(0xFFFFFFFF),
        activePill = Color(0xFFFACC15),
        textMuted = Color(0xFF9CA3AF)
    )
}
