package com.example.tts

import com.example.ai.GeminiToneResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RespiratoryArtifact(val description: String, val audioTag: String) {
    INHALATION("Natural Inhalation", " [gasp] "),
    SOFT_EXHALE("Soft Exhale", " ... [sigh] ... "),
    CHUCKLE("Light Chuckle", " [chuckle] "),
    MOUTH_CLICK("Soft Pause Click", " ... ")
}

data class RespiratoryProsody(
    val processedText: String,
    val stability: Double,
    val style: Double,
    val similarityBoost: Double,
    val artifact: RespiratoryArtifact? = null,
    val pauseDurationMs: Long = 0L
)

/**
 * Respiratory Synthesis Manager
 * Intelligently manages non-lexical audio artifacts (inhalations, exhales, subtle pauses)
 * at logical pause points detected by the sentence parser, without changing the active voice ID.
 */
object RespiratorySynthesisManager {

    private val _currentArtifact = MutableStateFlow<RespiratoryArtifact?>(null)
    val currentArtifact: StateFlow<RespiratoryArtifact?> = _currentArtifact.asStateFlow()

    private var isEnabled = true

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Integrates Gemini tone results with sentence structure to compute speech parameters
     * and insert non-lexical artifacts at paragraph breaks or long sentence pauses.
     */
    fun processSentence(
        rawText: String,
        geminiTone: GeminiToneResult?,
        isParagraphEnd: Boolean = false,
        sentenceIndex: Int = 0
    ): RespiratoryProsody {
        if (!isEnabled || rawText.isBlank()) {
            return RespiratoryProsody(
                processedText = rawText,
                stability = geminiTone?.stability ?: 0.38,
                style = geminiTone?.style ?: 0.35,
                similarityBoost = geminiTone?.similarityBoost ?: 0.85
            )
        }

        var text = rawText.trim()
        var stability = geminiTone?.stability ?: 0.38
        var style = geminiTone?.style ?: 0.35
        var similarityBoost = geminiTone?.similarityBoost ?: 0.85
        var artifact: RespiratoryArtifact? = null
        var pauseMs = 0L

        // Detect non-lexical artifacts from Gemini tone or text cues
        when (geminiTone?.nonLexicalArtifact) {
            "inhalation" -> {
                artifact = RespiratoryArtifact.INHALATION
                pauseMs = 180L
            }
            "exhale" -> {
                artifact = RespiratoryArtifact.SOFT_EXHALE
                pauseMs = 250L
            }
            "chuckle" -> {
                artifact = RespiratoryArtifact.CHUCKLE
                pauseMs = 120L
            }
        }

        // Paragraph boundary natural breath insertion
        if (isParagraphEnd && artifact == null && sentenceIndex > 0) {
            artifact = if (sentenceIndex % 3 == 0) RespiratoryArtifact.INHALATION else RespiratoryArtifact.SOFT_EXHALE
            pauseMs = 300L
        }

        // Sentence punctuation micro-pauses
        if (text.endsWith("?") || text.endsWith("!")) {
            pauseMs = pauseMs.coerceAtLeast(200L)
        } else if (!text.contains(",") && !text.contains(".") && text.length > 90) {
            // Insert subtle comma pause in long unbroken clauses
            val mid = text.length / 2
            val spaceIdx = text.indexOf(' ', mid)
            if (spaceIdx > 0) {
                text = text.substring(0, spaceIdx) + ", " + text.substring(spaceIdx + 1)
            }
        }

        _currentArtifact.value = artifact

        return RespiratoryProsody(
            processedText = text,
            stability = stability,
            style = style,
            similarityBoost = similarityBoost,
            artifact = artifact,
            pauseDurationMs = pauseMs
        )
    }
}
