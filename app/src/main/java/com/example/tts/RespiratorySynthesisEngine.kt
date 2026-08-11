package com.example.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class HumanRespiratoryEvent {
    object Idle : HumanRespiratoryEvent()
    object Inhaling : HumanRespiratoryEvent()
    object Speaking : HumanRespiratoryEvent()
    object Chuckling : HumanRespiratoryEvent()
    object Exhaling : HumanRespiratoryEvent()
    data class PausingToCheckUp(val prompt: String = "Pause to check up on listener") : HumanRespiratoryEvent()
}

data class ProsodyMarkup(
    val processedText: String,
    val stability: Double = 0.38,
    val style: Double = 0.35,
    val similarityBoost: Double = 0.85,
    val primaryEvent: HumanRespiratoryEvent = HumanRespiratoryEvent.Speaking
)

object RespiratorySynthesisEngine {

    private val _respiratoryState = MutableStateFlow<HumanRespiratoryEvent>(HumanRespiratoryEvent.Idle)
    val respiratoryState: StateFlow<HumanRespiratoryEvent> = _respiratoryState.asStateFlow()

    /**
     * Parses emotional tags and punctuation to generate human prosody markers and breath cues.
     */
    fun parseAndDecorate(rawText: String): ProsodyMarkup {
        val lower = rawText.lowercase()

        var stability = 0.38
        var style = 0.35
        var similarityBoost = 0.85
        var event: HumanRespiratoryEvent = HumanRespiratoryEvent.Speaking

        var text = rawText

        if (lower.contains("haha") || lower.contains("lol") || lower.contains("[giggle]") || lower.contains("[laugh]") || lower.contains("[chuckle]")) {
            stability = 0.22
            style = 0.65
            event = HumanRespiratoryEvent.Chuckling
            text = text.replace(Regex("\\[(giggle|laugh|chuckle)\\]", RegexOption.IGNORE_CASE), " [chuckle] ")
        } else if (lower.contains("[sigh]") || lower.contains("[exhale]") || lower.contains("sigh")) {
            stability = 0.45
            style = 0.40
            event = HumanRespiratoryEvent.Exhaling
            text = text.replace(Regex("\\[(sigh|exhale)\\]", RegexOption.IGNORE_CASE), " ... [sigh] ... ")
        } else if (lower.contains("[gasp]") || lower.contains("[inhale]") || lower.contains("wow!")) {
            stability = 0.28
            style = 0.55
            event = HumanRespiratoryEvent.Inhaling
            text = text.replace(Regex("\\[(gasp|inhale)\\]", RegexOption.IGNORE_CASE), " ... [gasp] ")
        } else if (rawText.endsWith("?") || lower.contains("check in") || lower.contains("listen")) {
            stability = 0.40
            style = 0.45
            event = HumanRespiratoryEvent.PausingToCheckUp("Interactive pause")
        }

        // Natural breath boundary injection for long sentences without punctuation
        if (!text.contains(".") && !text.contains(",") && text.length > 80) {
            val midPoint = text.length / 2
            val spaceIdx = text.indexOf(' ', midPoint)
            if (spaceIdx > 0) {
                text = text.substring(0, spaceIdx) + ", ... " + text.substring(spaceIdx + 1)
            }
        }

        _respiratoryState.value = event
        return ProsodyMarkup(
            processedText = text,
            stability = stability,
            style = style,
            similarityBoost = similarityBoost,
            primaryEvent = event
        )
    }

    fun updateRespiratoryState(event: HumanRespiratoryEvent) {
        _respiratoryState.value = event
    }
}
