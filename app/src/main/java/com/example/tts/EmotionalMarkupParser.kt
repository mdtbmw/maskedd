package com.example.tts

object EmotionalMarkupParser {

    /**
     * Analyzes document segments for emotional tone (e.g. joyful, sarcastic, tense, dramatic)
     * and maps tone to ElevenLabs / Fish.audio style and stability settings.
     */
    fun analyzeAndInjectMarkup(rawText: String): ProsodyMarkup {
        val lower = rawText.lowercase()

        var style = 0.35
        var stability = 0.38
        var similarityBoost = 0.85
        var event: HumanRespiratoryEvent = HumanRespiratoryEvent.Speaking

        var text = rawText

        when {
            lower.contains("excited") || lower.contains("happy") || lower.contains("joy") || lower.contains("amazing") -> {
                style = 0.60
                stability = 0.25
                event = HumanRespiratoryEvent.Chuckling
            }
            lower.contains("whisper") || lower.contains("quiet") || lower.contains("secret") || lower.contains("softly") -> {
                style = 0.45
                stability = 0.50
                event = HumanRespiratoryEvent.Exhaling
                text = " ... [soft breath] ... $text"
            }
            lower.contains("suddenly") || lower.contains("shock") || lower.contains("gasp") || lower.contains("fear") -> {
                style = 0.55
                stability = 0.28
                event = HumanRespiratoryEvent.Inhaling
                text = " ... [gasp] $text"
            }
            lower.contains("question") || lower.endsWith("?") -> {
                style = 0.42
                stability = 0.35
                event = HumanRespiratoryEvent.PausingToCheckUp("Inquisitive tone")
            }
        }

        // Delegate to RespiratorySynthesisEngine for final formatting
        val baseProsody = RespiratorySynthesisEngine.parseAndDecorate(text)
        return baseProsody.copy(
            stability = if (baseProsody.stability != 0.38) baseProsody.stability else stability,
            style = if (baseProsody.style != 0.35) baseProsody.style else style,
            similarityBoost = similarityBoost
        )
    }
}
