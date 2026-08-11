package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.ai.GeminiTextPreprocessor
import com.example.parser.ParsedDocument
import com.example.parser.SentenceToken
import com.example.parser.WordToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.sin

enum class TtsEngineType(val displayName: String, val description: String) {
    DEVICE_NATIVE_TTS("Standard TTS", "100% offline built-in device speech engine"),
    MASKED_AI_VOICE("Masked AI Voice", "Biological neural AI voice engine with lookahead streaming"),
    KOKORO_LOCAL_NEURAL("Kokoro Offline AI", "100% offline high-fidelity local neural TTS model")
}

data class PlaybackProgress(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentWordIndex: Int = 0,
    val currentSentenceIndex: Int = 0,
    val currentWord: String = "",
    val wordSyncProgress: Float = 0f, // 0.0f..1.0f sub-word high-frame-rate progress for karaoke
    val totalWords: Int = 0,
    val totalSentences: Int = 0,
    val progressPercentage: Float = 0f,
    val currentReadingMode: ReadingMode = ReadingMode.STORYTELLER,
    val speedRate: Float = 1.0f,
    val pitchMultiplier: Float = 1.0f,
    val visualizerAmplitudes: List<Float> = List(16) { 0.1f },
    val engineType: TtsEngineType = TtsEngineType.MASKED_AI_VOICE,
    val isAutoFallbackActive: Boolean = false,
    val engineStatusMessage: String = "Masked AI Neural Voice Active",
    val syncOffsetMs: Int = 0,
    val ambientTrack: AmbientTrack = AmbientTrack.OFF,
    val ambientVolume: Float = 0.25f,
    val lowPassRatio: Float = 0.45f
)

/**
 * Custom thread-safe Circular Audio Queue Buffer for TTS utterance chunk streaming
 * and look-ahead pre-fetching to eliminate gaps, pops, and stutter.
 */
class CircularUtteranceBuffer(val capacity: Int = 4) {
    private val buffer = Array<String?>(capacity) { null }
    private var head = 0
    private var tail = 0
    private var count = 0

    @Synchronized
    fun push(utteranceId: String): Boolean {
        if (count >= capacity) return false
        buffer[tail] = utteranceId
        tail = (tail + 1) % capacity
        count++
        return true
    }

    @Synchronized
    fun pop(): String? {
        if (count == 0) return null
        val item = buffer[head]
        buffer[head] = null
        head = (head + 1) % capacity
        count--
        return item
    }

    @Synchronized
    fun clear() {
        for (i in buffer.indices) buffer[i] = null
        head = 0
        tail = 0
        count = 0
    }

    val isFull: Boolean
        @Synchronized get() = count >= capacity

    val isEmpty: Boolean
        @Synchronized get() = count == 0
}

class SpeechEngine(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    val ambientMusicPlayer = AmbientMusicPlayer(scope)
    val audioAtmosphereService = AudioAtmosphereService(scope, ambientMusicPlayer)
    val cloudVoiceSynthesizer = CloudVoiceSynthesizer(context.applicationContext, scope)
    val kokoroLocalEngine = KokoroLocalTtsEngine(context.applicationContext, scope)
    val fireRedSynthesizer = FireRedLongCatSynthesizer(context.applicationContext, scope)
    val zeroDowntimeRouter = ZeroDowntimeAudioRouter(context.applicationContext, scope, cloudVoiceSynthesizer, fireRedSynthesizer, kokoroLocalEngine)
    private val circularUtteranceBuffer = CircularUtteranceBuffer(capacity = 4)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pause()
            }
        }
    }

    private val _progressState = MutableStateFlow(PlaybackProgress())
    val progressState: StateFlow<PlaybackProgress> = _progressState.asStateFlow()

    private val _engineState = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    val engineState: StateFlow<EnginePlaybackState> = _engineState.asStateFlow()

    private var currentParsedDoc: ParsedDocument? = null
    private var currentMode: ReadingMode = ReadingMode.STORYTELLER
    private var customSpeed: Float = 1.0f
    private var customPitch: Float = 1.0f
    private var currentEngineType: TtsEngineType = TtsEngineType.MASKED_AI_VOICE

    private var playbackJob: Job? = null
    private var currentTargetWordIndex = 0

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                setupUtteranceListener()
            }
        }
    }

    private fun transitionTo(newState: EnginePlaybackState): Boolean {
        val currentState = _engineState.value
        if (!currentState.canTransitionTo(newState)) {
            TtsDiagnosticLogger.log(
                eventType = LogEventType.ILLEGAL_TRANSITION,
                message = "Illegal transition blocked: ${currentState.name} -> ${newState.name}",
                stateFrom = currentState.name,
                stateTo = newState.name,
                isError = true
            )
            return false
        }
        _engineState.value = newState
        TtsDiagnosticLogger.log(
            eventType = LogEventType.STATE_TRANSITION,
            message = "State changed: ${currentState.name} -> ${newState.name}",
            stateFrom = currentState.name,
            stateTo = newState.name
        )
        return true
    }

    private var lastRangeCallbackTime = 0L
    private var currentWordStartTime = 0L
    private var estimatedWordDurationMs = 250L

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val doc = currentParsedDoc ?: return
                val sentenceIdx = utteranceId?.substringAfter("sent_")?.toIntOrNull() ?: return
                val sentence = doc.sentences.getOrNull(sentenceIdx) ?: return

                TtsDiagnosticLogger.log(
                    eventType = LogEventType.TTS_EVENT,
                    message = "TTS utterance started: sent_$sentenceIdx '${sentence.text.take(30)}...'"
                )

                lastRangeCallbackTime = 0L
                currentTargetWordIndex = sentence.startWordIndex
                val wordText = doc.words.getOrNull(currentTargetWordIndex)?.word ?: ""
                updateCurrentWord(currentTargetWordIndex, sentenceIdx, wordText)

                ambientMusicPlayer.setTtsActive(true)
                _progressState.value = _progressState.value.copy(
                    isPlaying = true,
                    isPaused = false,
                    currentSentenceIndex = sentenceIdx
                )
            }

            override fun onDone(utteranceId: String?) {
                val doc = currentParsedDoc ?: return
                val sentenceIdx = utteranceId?.substringAfter("sent_")?.toIntOrNull() ?: return

                TtsDiagnosticLogger.log(
                    eventType = LogEventType.TTS_EVENT,
                    message = "TTS utterance completed: sent_$sentenceIdx"
                )

                circularUtteranceBuffer.pop()

                if (sentenceIdx >= doc.sentences.size - 1) {
                    _progressState.value = _progressState.value.copy(isPlaying = false, isPaused = false)
                    ambientMusicPlayer.setTtsActive(false)
                    ambientMusicPlayer.stop()
                    transitionTo(EnginePlaybackState.Idle)
                } else if (_progressState.value.isPlaying) {
                    val nextToQueue = sentenceIdx + 2
                    if (nextToQueue < doc.sentences.size) {
                        speakSentence(nextToQueue, TextToSpeech.QUEUE_ADD)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.TTS_EVENT,
                    message = "TTS engine error for utterance $utteranceId",
                    isError = true
                )
                _progressState.value = _progressState.value.copy(isPlaying = false)
                ambientMusicPlayer.setTtsActive(false)
                ambientMusicPlayer.stop()
                transitionTo(EnginePlaybackState.Error("Native TTS playback error on utterance $utteranceId"))
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                val now = System.currentTimeMillis()
                val offset = if (currentWordStartTime > 0) now - currentWordStartTime - estimatedWordDurationMs else 0L
                lastRangeCallbackTime = now
                
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.TIMING_OFFSET,
                    message = "TTS word range callback offset: ${offset}ms",
                    timingOffsetMs = offset
                )

                handleWordRangeCallback(utteranceId, start, end)
            }
        })
    }

    private fun speakSentence(sIdx: Int, queueMode: Int) {
        val doc = currentParsedDoc ?: return
        val sentence = doc.sentences.getOrNull(sIdx) ?: return
        val utteranceId = "sent_$sIdx"
        
        circularUtteranceBuffer.push(utteranceId)

        if (tts == null) {
            try {
                tts = TextToSpeech(context.applicationContext, this)
            } catch (_: Exception) {}
        }

        applyVoiceParameters()

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val baseText = sentence.spokenText.ifBlank { sentence.text }
        val normalizedText = TextNormalizationPipeline.processFullPipeline(baseText).coarticulatedText
        try {
            tts?.speak(normalizedText, queueMode, params, utteranceId)
        } catch (e: Exception) {
            TtsDiagnosticLogger.log(LogEventType.TTS_EVENT, "TTS speak exception: ${e.message}", isError = true)
        }
    }

    private fun handleWordRangeCallback(utteranceId: String?, charStart: Int, charEnd: Int) {
        val doc = currentParsedDoc ?: return
        val sentenceIdx = utteranceId?.substringAfter("sent_")?.toIntOrNull() ?: return
        val sentence = doc.sentences.getOrNull(sentenceIdx) ?: return

        lastRangeCallbackTime = System.currentTimeMillis()

        // Proportionally map character offset from spokenText to original sentence words
        val spokenLen = sentence.spokenText.length.coerceAtLeast(1)
        val origLen = sentence.text.length.coerceAtLeast(1)
        val mappedCharStart = ((charStart.toFloat() / spokenLen) * origLen).toInt()

        val matchingWord = sentence.words.find { word ->
            val relWordStart = (word.startCharOffset - sentence.startCharOffset).coerceAtLeast(0)
            val relWordEnd = (word.endCharOffset - sentence.startCharOffset).coerceAtLeast(0)
            mappedCharStart >= relWordStart && mappedCharStart <= relWordEnd
        } ?: sentence.words.find { word ->
            val relWordStart = (word.startCharOffset - sentence.startCharOffset).coerceAtLeast(0)
            relWordStart >= mappedCharStart
        } ?: sentence.words.firstOrNull() ?: doc.words.getOrNull(sentence.startWordIndex)

        if (matchingWord != null) {
            currentTargetWordIndex = matchingWord.wordIndex
            updateCurrentWord(matchingWord.wordIndex, matchingWord.sentenceIndex, matchingWord.word)
            generateDynamicVisualizerAmplitudes()
        }
    }

    /**
     * Set the active document and reset progress.
     */
    fun loadDocument(doc: ParsedDocument, startWordIndex: Int = 0) {
        stop()
        currentParsedDoc = doc
        currentTargetWordIndex = startWordIndex.coerceIn(0, (doc.words.size - 1).coerceAtLeast(0))

        val initialSentence = doc.words.getOrNull(currentTargetWordIndex)?.sentenceIndex ?: 0
        val wordText = doc.words.getOrNull(currentTargetWordIndex)?.word ?: ""

        TtsDiagnosticLogger.log(
            LogEventType.TTS_EVENT,
            "Loaded document '${doc.title}' with ${doc.words.size} words, ${doc.sentences.size} sentences."
        )

        _progressState.value = PlaybackProgress(
            isPlaying = false,
            isPaused = false,
            currentWordIndex = currentTargetWordIndex,
            currentSentenceIndex = initialSentence,
            currentWord = wordText,
            totalWords = doc.words.size,
            totalSentences = doc.sentences.size,
            progressPercentage = if (doc.words.isNotEmpty()) currentTargetWordIndex.toFloat() / doc.words.size else 0f,
            currentReadingMode = currentMode,
            speedRate = customSpeed,
            pitchMultiplier = customPitch,
            ambientTrack = ambientMusicPlayer.currentTrack.value,
            ambientVolume = ambientMusicPlayer.volume.value
        )
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attr)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (_: Exception) {}
    }

    /**
     * Start / Resume audio speech execution cleanly using circular utterance queue and sealed state machine.
     */
    fun play() {
        val doc = currentParsedDoc ?: return
        if (doc.sentences.isEmpty() || doc.words.isEmpty()) return

        requestAudioFocus()
        playbackJob?.cancel()

        when (currentEngineType) {
            TtsEngineType.MASKED_AI_VOICE -> playCloudPlayback()
            TtsEngineType.KOKORO_LOCAL_NEURAL -> playKokoroLocalPlayback()
            TtsEngineType.DEVICE_NATIVE_TTS -> {
                val startSentenceIdx = doc.words.getOrNull(currentTargetWordIndex)?.sentenceIndex ?: 0
                val synthState = EnginePlaybackState.SynthesizingTts(startSentenceIdx, "Synthesizing TTS buffer")
                transitionTo(synthState)
                startNativePlayback()
            }
        }
    }

    private fun playKokoroLocalPlayback() {
        val doc = currentParsedDoc ?: return
        val startSentenceIdx = doc.words.getOrNull(currentTargetWordIndex)?.sentenceIndex ?: 0

        cloudVoiceSynthesizer.stop()
        try { tts?.stop() } catch (_: Exception) {}

        val playState = EnginePlaybackState.Playing(
            currentWordIndex = currentTargetWordIndex,
            currentSentenceIndex = startSentenceIdx,
            isAiMode = false
        )
        transitionTo(playState)
        ambientMusicPlayer.setTtsActive(true)
        ambientMusicPlayer.start()

        _progressState.value = _progressState.value.copy(
            isPlaying = true,
            isPaused = false,
            currentSentenceIndex = startSentenceIdx,
            engineStatusMessage = "Kokoro Offline Neural TTS Active"
        )

        kokoroLocalEngine.speakSentence(
            doc = doc,
            sentenceIndex = startSentenceIdx,
            speechRate = customSpeed * currentMode.speechRate,
            pitch = customPitch * currentMode.pitch,
            listener = object : LocalSynthesisListener {
                override fun onSynthesisStart(sentenceIndex: Int, text: String) {}

                override fun onWordHighlight(wordIndex: Int, word: String) {
                    currentTargetWordIndex = wordIndex
                    updateCurrentWord(wordIndex, startSentenceIdx, word)
                    generateDynamicVisualizerAmplitudes()
                }

                override fun onSentenceCompleted(sentenceIndex: Int) {
                    if (sentenceIndex < doc.sentences.size - 1) {
                        val nextSentence = doc.sentences[sentenceIndex + 1]
                        currentTargetWordIndex = nextSentence.startWordIndex
                        playKokoroLocalPlayback()
                    } else {
                        _progressState.value = _progressState.value.copy(isPlaying = false, isPaused = false)
                        ambientMusicPlayer.setTtsActive(false)
                        ambientMusicPlayer.stop()
                        abandonAudioFocus()
                        transitionTo(EnginePlaybackState.Idle)
                    }
                }

                override fun onError(errorMessage: String) {
                    startNativePlayback()
                }
            }
        )
    }

    private fun playCloudPlayback() {
        val doc = currentParsedDoc ?: return
        val startSentenceIdx = doc.words.getOrNull(currentTargetWordIndex)?.sentenceIndex ?: 0
        val sentence = doc.sentences.getOrNull(startSentenceIdx) ?: return

        // PREVENT DUAL VOICES: Explicitly silence and stop native TTS engine
        try {
            tts?.stop()
            circularUtteranceBuffer.clear()
        } catch (_: Exception) {}

        val playState = EnginePlaybackState.Playing(
            currentWordIndex = currentTargetWordIndex,
            currentSentenceIndex = startSentenceIdx,
            isAiMode = true
        )
        transitionTo(playState)
        ambientMusicPlayer.setTtsActive(true)
        ambientMusicPlayer.start()

        _progressState.value = _progressState.value.copy(
            isPlaying = true,
            isPaused = false,
            currentSentenceIndex = startSentenceIdx,
            engineStatusMessage = "Masked AI Active"
        )

        cloudVoiceSynthesizer.synthesizeAndPlay(
            sentenceIndex = startSentenceIdx,
            rawText = sentence.text,
            doc = doc,
            listener = object : CloudSynthesisListener {
                override fun onSynthesisStart(text: String) {
                    _progressState.value = _progressState.value.copy(
                        isPlaying = true,
                        isPaused = false,
                        currentSentenceIndex = startSentenceIdx,
                        engineStatusMessage = "Masked AI Active"
                    )
                }

                override fun onPlaybackProgress(progressFraction: Float, currentAudioPositionMs: Int, totalDurationMs: Int) {
                    val sentenceWords = sentence.words
                    if (sentenceWords.isNotEmpty()) {
                        val wordIdxInSentence = (progressFraction * sentenceWords.size).toInt().coerceIn(0, sentenceWords.size - 1)
                        val activeWord = sentenceWords[wordIdxInSentence]
                        currentTargetWordIndex = activeWord.wordIndex
                        updateCurrentWord(activeWord.wordIndex, startSentenceIdx, activeWord.word)
                        generateDynamicVisualizerAmplitudes()
                    }
                }

                override fun onPlaybackCompleted() {
                    if (startSentenceIdx < doc.sentences.size - 1) {
                        val nextSentence = doc.sentences[startSentenceIdx + 1]
                        currentTargetWordIndex = nextSentence.startWordIndex
                        playCloudPlayback()
                    } else {
                        _progressState.value = _progressState.value.copy(isPlaying = false, isPaused = false)
                        ambientMusicPlayer.setTtsActive(false)
                        ambientMusicPlayer.stop()
                        abandonAudioFocus()
                        transitionTo(EnginePlaybackState.Idle)
                    }
                }

                override fun onErrorAndFallback(errorMessage: String) {
                    triggerAutoFallbackToDeviceTts("Masked AI Fallback: $errorMessage")
                    startNativePlayback()
                }
            }
        )
    }

    private fun startNativePlayback() {
        val doc = currentParsedDoc ?: return
        if (doc.sentences.isEmpty() || doc.words.isEmpty()) return

        try {
            // PREVENT DUAL VOICES: Explicitly silence and stop cloud voice synthesizer
            cloudVoiceSynthesizer.stop()
            tts?.stop()
            circularUtteranceBuffer.clear()

            applyVoiceParameters()

            val startSentenceIdx = doc.words.getOrNull(currentTargetWordIndex)?.sentenceIndex ?: 0

            val playState = EnginePlaybackState.Playing(
                currentWordIndex = currentTargetWordIndex,
                currentSentenceIndex = startSentenceIdx,
                isAiMode = currentEngineType == TtsEngineType.MASKED_AI_VOICE
            )
            
            if (!transitionTo(playState)) {
                // If direct transition to Playing was disallowed (e.g. from ProcessingAi), force transition via SynthesizingTts
                val synthState = EnginePlaybackState.SynthesizingTts(startSentenceIdx)
                _engineState.value = synthState
                transitionTo(playState)
            }

            // Populate circular buffer with initial sentences
            speakSentence(startSentenceIdx, TextToSpeech.QUEUE_FLUSH)
            if (startSentenceIdx + 1 < doc.sentences.size) {
                speakSentence(startSentenceIdx + 1, TextToSpeech.QUEUE_ADD)
            }

            ambientMusicPlayer.setTtsActive(true)
            _progressState.value = _progressState.value.copy(isPlaying = true, isPaused = false)

            // Start background ambient music track if enabled
            ambientMusicPlayer.start()

            // Launch high-frame-rate ~60fps progress animation job for word-sync timing & visualizer waveform
            playbackJob = scope.launch(Dispatchers.Default) {
                var frameCounter = 0
                while (_progressState.value.isPlaying) {
                    frameCounter++
                    if (frameCounter % 6 == 0) {
                        generateDynamicVisualizerAmplitudes()
                    }

                    // High-frame-rate intra-word karaoke highlight progress calculation
                    val now = System.currentTimeMillis()
                    val wordElapsed = now - currentWordStartTime
                    val syncFrac = (wordElapsed.toFloat() / estimatedWordDurationMs.toFloat()).coerceIn(0f, 1f)

                    _progressState.value = _progressState.value.copy(wordSyncProgress = syncFrac)

                    // Seamless word progression within active sentence without interrupting speech playback
                    if (now - currentWordStartTime >= estimatedWordDurationMs) {
                        val currentSentenceIndex = _progressState.value.currentSentenceIndex
                        val currentSentence = doc.sentences.getOrNull(currentSentenceIndex)
                        if (currentSentence != null) {
                            if (currentTargetWordIndex < currentSentence.endWordIndex) {
                                if (lastRangeCallbackTime == 0L || now - lastRangeCallbackTime > 250L) {
                                    currentTargetWordIndex++
                                    val nextWord = doc.words.getOrNull(currentTargetWordIndex)
                                    if (nextWord != null) {
                                        updateCurrentWord(nextWord.wordIndex, nextWord.sentenceIndex, nextWord.word)
                                    }
                                }
                            }
                        }
                    }

                    delay(16L) // ~60 FPS high-frame-rate animation refresh rate
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorState = EnginePlaybackState.Error("Playback initialization failed: ${e.message}", fallbackActive = true)
            transitionTo(errorState)
        }
    }

    private fun updateCurrentWord(wordIndex: Int, sentenceIndex: Int, wordText: String) {
        val doc = currentParsedDoc ?: return
        val total = doc.words.size.coerceAtLeast(1)
        val progressPercent = wordIndex.toFloat() / total

        currentWordStartTime = System.currentTimeMillis()
        val effectiveSpeed = (currentMode.speechRate * customSpeed).coerceAtLeast(0.1f)
        estimatedWordDurationMs = ((wordText.length * 55L + 110L) / effectiveSpeed).toLong().coerceIn(80L, 2500L)

        _progressState.value = _progressState.value.copy(
            currentWordIndex = wordIndex,
            currentSentenceIndex = sentenceIndex,
            currentWord = wordText,
            wordSyncProgress = 0f,
            progressPercentage = progressPercent
        )
    }

    private fun applyVoiceParameters() {
        try {
            val isAiMode = currentEngineType == TtsEngineType.MASKED_AI_VOICE
            val expressivePitchMultiplier = if (isAiMode) 1.10f else 1.0f
            val expressiveRateMultiplier = if (isAiMode) 0.95f else 1.0f

            val effectivePitch = (currentMode.pitch * customPitch * expressivePitchMultiplier).coerceIn(0.5f, 2.0f)
            val effectiveRate = (currentMode.speechRate * customSpeed * expressiveRateMultiplier).coerceIn(0.1f, 3.0f)

            tts?.setPitch(effectivePitch)
            tts?.setSpeechRate(effectiveRate)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Generates rhythmic waveform visualizer amplitudes for the UI.
     */
    private fun generateDynamicVisualizerAmplitudes() {
        val now = System.currentTimeMillis() / 100.0
        val amplitudes = List(16) { i ->
            val wave = (sin(now + i * 0.4) * 0.4 + sin(now * 1.5 + i * 0.2) * 0.3 + 0.5).toFloat()
            wave.coerceIn(0.15f, 0.98f)
        }
        _progressState.value = _progressState.value.copy(visualizerAmplitudes = amplitudes)
    }

    fun pause() {
        abandonAudioFocus()
        playbackJob?.cancel()
        cloudVoiceSynthesizer.pause()
        tts?.stop()
        circularUtteranceBuffer.clear()
        ambientMusicPlayer.setTtsActive(false)
        ambientMusicPlayer.stop()
        transitionTo(
            EnginePlaybackState.Paused(
                currentWordIndex = currentTargetWordIndex,
                currentSentenceIndex = _progressState.value.currentSentenceIndex
            )
        )
        _progressState.value = _progressState.value.copy(
            isPlaying = false,
            isPaused = true,
            wordSyncProgress = 0f,
            visualizerAmplitudes = List(16) { 0.1f }
        )
    }

    fun stop() {
        abandonAudioFocus()
        playbackJob?.cancel()
        cloudVoiceSynthesizer.stop()
        tts?.stop()
        circularUtteranceBuffer.clear()
        ambientMusicPlayer.setTtsActive(false)
        ambientMusicPlayer.stop()
        transitionTo(EnginePlaybackState.Idle)
        _progressState.value = _progressState.value.copy(
            isPlaying = false,
            isPaused = false,
            wordSyncProgress = 0f,
            visualizerAmplitudes = List(16) { 0.1f }
        )
    }

    fun setAmbientTrack(track: AmbientTrack) {
        ambientMusicPlayer.setTrack(track)
        _progressState.value = _progressState.value.copy(ambientTrack = track)
    }

    fun setAmbientVolume(vol: Float) {
        ambientMusicPlayer.setVolume(vol)
        _progressState.value = _progressState.value.copy(ambientVolume = vol)
    }

    fun setLowPassRatio(ratio: Float) {
        ambientMusicPlayer.setLowPassRatio(ratio)
        _progressState.value = _progressState.value.copy(lowPassRatio = ratio)
    }

    fun seekToWord(wordIndex: Int) {
        val doc = currentParsedDoc ?: return
        val clampedIndex = wordIndex.coerceIn(0, (doc.words.size - 1).coerceAtLeast(0))
        currentTargetWordIndex = clampedIndex

        val sentenceIdx = doc.words.getOrNull(clampedIndex)?.sentenceIndex ?: 0
        val wordText = doc.words.getOrNull(clampedIndex)?.word ?: ""

        val wasPlaying = _progressState.value.isPlaying
        stop()

        updateCurrentWord(clampedIndex, sentenceIdx, wordText)

        if (wasPlaying) {
            play()
        }
    }

    fun seekToSentence(sentenceIndex: Int) {
        val doc = currentParsedDoc ?: return
        val sentence = doc.sentences.getOrNull(sentenceIndex.coerceIn(0, (doc.sentences.size - 1).coerceAtLeast(0))) ?: return
        seekToWord(sentence.startWordIndex)
    }

    fun jumpForward(seconds: Int = 10) {
        val estimatedWords = seconds * 3 // approx 3 words per sec
        seekToWord(_progressState.value.currentWordIndex + estimatedWords)
    }

    fun jumpBackward(seconds: Int = 10) {
        val estimatedWords = seconds * 3
        seekToWord(_progressState.value.currentWordIndex - estimatedWords)
    }

    fun setReadingMode(mode: ReadingMode) {
        currentMode = mode
        _progressState.value = _progressState.value.copy(currentReadingMode = mode)
        if (_progressState.value.isPlaying) {
            play()
        }
    }

    fun setSpeed(rate: Float) {
        customSpeed = rate
        _progressState.value = _progressState.value.copy(speedRate = rate)
        if (_progressState.value.isPlaying) {
            play()
        }
    }

    fun setPitch(pitch: Float) {
        customPitch = pitch
        _progressState.value = _progressState.value.copy(pitchMultiplier = pitch)
        if (_progressState.value.isPlaying) {
            play()
        }
    }

    fun setEngineType(engineType: TtsEngineType) {
        if (currentEngineType == engineType) return
        val wasPlaying = _progressState.value.isPlaying
        stop()
        currentEngineType = engineType
        val statusMsg = if (engineType == TtsEngineType.DEVICE_NATIVE_TTS) {
            "Standard TTS Active (100% Offline)"
        } else {
            "Masked AI Neural Voice Active"
        }
        _progressState.value = _progressState.value.copy(
            engineType = engineType,
            isAutoFallbackActive = false,
            engineStatusMessage = statusMsg
        )
        if (wasPlaying) {
            play()
        }
    }

    fun triggerAutoFallbackToDeviceTts(reason: String = "Temporary cloud latency") {
        TtsDiagnosticLogger.log(
            eventType = LogEventType.TTS_EVENT,
            message = "Transient fallback to device speech engine ($reason); preserving Masked AI Voice selection.",
            isError = false
        )
        _progressState.value = _progressState.value.copy(
            isAutoFallbackActive = true,
            engineStatusMessage = "Masked AI active (sub-sentence fallback: $reason)"
        )
    }

    fun setSyncOffsetMs(offsetMs: Int) {
        _progressState.value = _progressState.value.copy(
            syncOffsetMs = offsetMs.coerceIn(-300, 300)
        )
    }

    fun speakRawText(text: String) {
        if (!isInitialized || text.isBlank()) return
        stop()
        tts?.setSpeechRate(customSpeed * currentMode.speechRate)
        tts?.setPitch(customPitch * currentMode.pitch)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "socratic_answer_utterance")
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
    }
}
