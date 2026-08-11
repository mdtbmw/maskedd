package com.example.tts

enum class DynamicFormatMode(
    val displayName: String,
    val subtitle: String,
    val iconName: String
) {
    DEEP_DIVE("Deep Dive Lyric Mode", "Full kinetic sentence scrolling & word synchronization", "AutoAwesome"),
    PODCAST_DEBATE("AI Podcast Debate Mode", "Converts chapter into a lively 2-host conversational podcast", "Podcasts"),
    KINETIC_SPRINT("TikTok Sprint Typography", "High-speed central word focal stream for rapid learning", "Speed"),
    DIALOGUE_ONLY("Dialogue Script Mode", "Filters prose to play character interactions like a movie script", "TheaterComedy")
}
