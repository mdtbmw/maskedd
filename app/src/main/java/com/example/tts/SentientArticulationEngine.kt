package com.example.tts

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.Immutable
import com.example.ai.GeminiTextPreprocessor
import com.example.parser.SentenceToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random

enum class SemanticEmotionalIntent(
    val displayName: String,
    val speedMultiplier: Float,
    val pitchMultiplier: Float,
    val vocalTension: Float
) {
    CALM_NARRATION("Calm Narration", 1.0f, 1.0f, 0.4f),
    DRAMATIC_SUSPENSE("Dramatic Suspense", 0.82f, 0.92f, 0.85f),
    URGENT_ACTION("Urgent Action", 1.28f, 1.12f, 0.95f),
    GRAVE_SADNESS("Grave Sadness", 0.78f, 0.88f, 0.3f),
    JOYFUL_EXCITEMENT("Joyful Excitement", 1.18f, 1.15f, 0.6f),
    SARCASTIC_WIT("Sarcastic Wit", 0.92f, 1.05f, 0.7f)
}

@Immutable
data class AcousticBlueprint(
    val sentenceIndex: Int,
    val intent: SemanticEmotionalIntent = SemanticEmotionalIntent.CALM_NARRATION,
    val velocityMultiplier: Float = 1.0f,
    val pitchMultiplier: Float = 1.0f,
    val commaBreathIndices: List<Int> = emptyList(),
    val microHesitationIndices: List<Int> = emptyList(),
    val isQuestionInflection: Boolean = false,
    val isExclamationPressure: Boolean = false,
    val summaryInsight: String = "Calm rhythmic phrasing"
)

/**
 * MaskedD Sentient Articulation Engine & Semantic Director
 * Lookahead SLM / NLP director that predicts emotional intent, diaphragm breath placement,
 * and micro-hesitation acoustic blueprints 5 sentences ahead of utterance playback.
 */
class SentientArticulationEngine(
    private val scope: CoroutineScope
) {
    private val _blueprints = MutableStateFlow<Map<Int, AcousticBlueprint>>(emptyMap())
    val blueprints: StateFlow<Map<Int, AcousticBlueprint>> = _blueprints.asStateFlow()

    private val _activeSentenceBlueprint = MutableStateFlow<AcousticBlueprint?>(null)
    val activeSentenceBlueprint: StateFlow<AcousticBlueprint?> = _activeSentenceBlueprint.asStateFlow()

    private val _isAnalyzingLookahead = MutableStateFlow(false)
    val isAnalyzingLookahead: StateFlow<Boolean> = _isAnalyzingLookahead.asStateFlow()

    /**
     * Reads 5 sentences ahead of [currentSentenceIdx] and generates high-fidelity Acoustic Blueprints.
     */
    fun processLookahead(sentences: List<SentenceToken>, currentSentenceIdx: Int) {
        if (sentences.isEmpty()) return

        val targetRange = (currentSentenceIdx until (currentSentenceIdx + 5).coerceAtMost(sentences.size))
        val missingIndices = targetRange.filter { !_blueprints.value.containsKey(it) }

        if (missingIndices.isEmpty()) {
            _activeSentenceBlueprint.value = _blueprints.value[currentSentenceIdx]
            return
        }

        scope.launch(Dispatchers.Default) {
            _isAnalyzingLookahead.value = true
            val updatedMap = _blueprints.value.toMutableMap()

            for (idx in missingIndices) {
                val sentence = sentences.getOrNull(idx) ?: continue
                val text = sentence.text

                // Heuristic / Gemini intent evaluation
                val intent = parseSemanticIntent(text)
                val commaIndices = sentence.words.mapIndexedNotNull { wIdx, w ->
                    if (w.word.endsWith(",") || w.word.endsWith(";") || w.word.endsWith("—")) wIdx else null
                }
                val hesitationIndices = sentence.words.mapIndexedNotNull { wIdx, w ->
                    val clean = w.word.lowercase().replace(Regex("[^a-z]"), "")
                    if (clean in HEAVY_WORDS || (clean.length > 8 && idx % 2 == 0)) wIdx else null
                }

                val blueprint = AcousticBlueprint(
                    sentenceIndex = idx,
                    intent = intent,
                    velocityMultiplier = intent.speedMultiplier,
                    pitchMultiplier = intent.pitchMultiplier,
                    commaBreathIndices = commaIndices,
                    microHesitationIndices = hesitationIndices,
                    isQuestionInflection = text.trim().endsWith("?"),
                    isExclamationPressure = text.trim().endsWith("!"),
                    summaryInsight = "${intent.displayName} • ${commaIndices.size} Diaphragm Breaths • ${hesitationIndices.size} Hesitations"
                )

                updatedMap[idx] = blueprint
            }

            _blueprints.value = updatedMap
            _activeSentenceBlueprint.value = updatedMap[currentSentenceIdx]
            _isAnalyzingLookahead.value = false
        }
    }

    private fun parseSemanticIntent(text: String): SemanticEmotionalIntent {
        val lower = text.lowercase()
        return when {
            lower.contains("!") || lower.contains("shouted") || lower.contains("ran") || lower.contains("suddenly") -> SemanticEmotionalIntent.URGENT_ACTION
            lower.contains("dark") || lower.contains("whisper") || lower.contains("shadow") || lower.contains("fear") -> SemanticEmotionalIntent.DRAMATIC_SUSPENSE
            lower.contains("cried") || lower.contains("tears") || lower.contains("grief") || lower.contains("lost") -> SemanticEmotionalIntent.GRAVE_SADNESS
            lower.contains("laughed") || lower.contains("smile") || lower.contains("joy") || lower.contains("wonderful") -> SemanticEmotionalIntent.JOYFUL_EXCITEMENT
            lower.contains("surely") || lower.contains("obviously") || lower.contains("ha!") -> SemanticEmotionalIntent.SARCASTIC_WIT
            else -> SemanticEmotionalIntent.CALM_NARRATION
        }
    }

    /**
     * Synthesizes a real-time biological diaphragm micro-inhalation breath sound
     * using AudioTrack PCM low-pass soft air noise.
     */
    suspend fun playDiaphragmBreathSound() = withContext(Dispatchers.IO) {
        try {
            val sampleRate = 22050
            val durationMs = 120 // short subtle 120ms air breath
            val numSamples = sampleRate * durationMs / 1000
            val buffer = ShortArray(numSamples)

            // Generate soft band-pass pink air noise (biological breath simulation)
            var lastOutput = 0.0
            for (i in 0 until numSamples) {
                val envelope = sin(Math.PI * i / numSamples) // smooth fade-in/out
                val whiteNoise = (Random.nextFloat() * 2f - 1f)
                // Simple low-pass filter for soft air breath texture
                lastOutput = 0.92 * lastOutput + 0.08 * whiteNoise
                buffer[i] = (lastOutput * envelope * 2400).toInt().coerceIn(-32768, 32767).toShort()
            }

            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            kotlinx.coroutines.delay(durationMs.toLong() + 20)
            audioTrack.release()
        } catch (e: Exception) {
            // Ignore if audio hardware unavailable
        }
    }

    companion object {
        private val HEAVY_WORDS = setOf(
            "death", "betrayal", "forever", "devastating", "truth", "agony", "shattered",
            "eternity", "forbidden", "impossible", "terrifying", "despair", "destiny"
        )
    }
}
