package com.example.tts

enum class ReadingMode(
    val title: String,
    val description: String,
    val pitch: Float,
    val speechRate: Float,
    val pauseFactorMs: Long,
    val iconName: String
) {
    STORYTELLER(
        title = "Storyteller",
        description = "Warm, expressive storytelling with rich narrative cadence",
        pitch = 0.90f,
        speechRate = 0.92f,
        pauseFactorMs = 400L,
        iconName = "AutoStories"
    ),
    FAST_READER(
        title = "Fast Reader",
        description = "High speed, energetic delivery for rapid scanning",
        pitch = 1.05f,
        speechRate = 1.35f,
        pauseFactorMs = 180L,
        iconName = "Speed"
    ),
    MONOTONE(
        title = "Monotone",
        description = "Steady, flat pitch for neutral, distraction-free reading",
        pitch = 1.00f,
        speechRate = 1.00f,
        pauseFactorMs = 300L,
        iconName = "GraphicEq"
    ),
    NEWS_ANCHOR(
        title = "News Anchor",
        description = "Crisp, articulate, professional broadcast cadence",
        pitch = 1.05f,
        speechRate = 1.15f,
        pauseFactorMs = 220L,
        iconName = "Campaign"
    ),
    BEDTIME_SOOTHE(
        title = "Bedtime Soothe",
        description = "Soft, tranquil, slow tone ideal for bedtime listening",
        pitch = 0.82f,
        speechRate = 0.80f,
        pauseFactorMs = 550L,
        iconName = "NightsStay"
    ),
    EDUCATOR(
        title = "Educator",
        description = "Clear, deliberate pace optimized for comprehension and study",
        pitch = 1.00f,
        speechRate = 0.95f,
        pauseFactorMs = 320L,
        iconName = "School"
    );

    companion object {
        fun fromName(name: String): ReadingMode {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: STORYTELLER
        }
    }
}
