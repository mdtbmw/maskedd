package com.example.tts

/**
 * Sealed State Machine for managing TTS speech synthesis, Gemini AI pre-processing,
 * audio playback, and automatic graceful fallback transitions.
 */
sealed class EnginePlaybackState {
    abstract val name: String

    object Idle : EnginePlaybackState() {
        override val name = "Idle"
    }

    data class ProcessingAi(
        val progress: Float = 0f,
        val statusMessage: String = "AI pre-processing text phonetics..."
    ) : EnginePlaybackState() {
        override val name = "ProcessingAi"
    }

    data class SynthesizingTts(
        val sentenceIndex: Int,
        val statusMessage: String = "Warming speech synthesis buffer..."
    ) : EnginePlaybackState() {
        override val name = "SynthesizingTts"
    }

    data class Playing(
        val currentWordIndex: Int,
        val currentSentenceIndex: Int,
        val isAiMode: Boolean = false,
        val statusMessage: String = "Playing audio"
    ) : EnginePlaybackState() {
        override val name = "Playing"
    }

    data class Paused(
        val currentWordIndex: Int,
        val currentSentenceIndex: Int
    ) : EnginePlaybackState() {
        override val name = "Paused"
    }

    data class Error(
        val errorMessage: String,
        val fallbackActive: Boolean = true,
        val activeEngine: TtsEngineType = TtsEngineType.MASKED_AI_VOICE
    ) : EnginePlaybackState() {
        override val name = "Error"
    }

    /**
     * Enforces strict state machine transition rules.
     * Prevents illegal direct transitions (e.g. ProcessingAi directly to Playing without going through SynthesizingTts).
     */
    fun canTransitionTo(targetState: EnginePlaybackState): Boolean {
        if (targetState is Idle || targetState is Error) return true

        return when (this) {
            is Idle -> targetState is ProcessingAi || targetState is SynthesizingTts || targetState is Playing
            is ProcessingAi -> targetState is SynthesizingTts || targetState is Error || targetState is Idle
            is SynthesizingTts -> targetState is Playing || targetState is Error || targetState is Idle
            is Playing -> targetState is Paused || targetState is SynthesizingTts || targetState is Idle || targetState is Error
            is Paused -> targetState is Playing || targetState is SynthesizingTts || targetState is Idle
            is Error -> targetState is Idle || targetState is ProcessingAi || targetState is SynthesizingTts || targetState is Playing
        }
    }
}

