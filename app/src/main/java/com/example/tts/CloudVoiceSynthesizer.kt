package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.ai.ApiKeyManager
import com.example.parser.ParsedDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface CloudSynthesisListener {
    fun onSynthesisStart(text: String)
    fun onPlaybackProgress(progressFraction: Float, currentAudioPositionMs: Int, totalDurationMs: Int)
    fun onPlaybackCompleted()
    fun onErrorAndFallback(errorMessage: String)
}

/**
 * MaskedD Cloud Voice Synthesizer
 * Provides zero-latency, lookahead-buffered neural AI voice playback.
 * Performs background model & key auto-rotation, pre-fetching upcoming sentences in background
 * so sentence-to-sentence transitions happen with 0ms delay and zero UI stuttering.
 */
class CloudVoiceSynthesizer(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val lruCacheManager = LruAudioCacheManager(context)

    private var mediaPlayer: MediaPlayer? = null
    private var nextMediaPlayer: MediaPlayer? = null
    private var trackingJob: Job? = null
    private var isPlayingActive = false

    // Concurrent pre-fetch dual-buffer cache: sentenceIndex -> Audio File
    private val audioCache = ConcurrentHashMap<Int, File>()
    private val fetchJobs = ConcurrentHashMap<Int, Job>()

    // Candidate ElevenLabs voice IDs for rotation fallback
    private val elevenLabsVoices = listOf(
        "EXAVITQu4vr4xnSDxMaL", // Rachel (Standard Premade)
        "cgSgspJ2msm6clMCkdW9", // Jessica
        "pFZP5JQG7iQjIQuC4Bku", // Lily
        "AZnzlk1XvdvUeBnXmlld"  // Domi
    )

    fun clearCache() {
        fetchJobs.values.forEach { it.cancel() }
        fetchJobs.clear()
        audioCache.values.forEach { file ->
            try { file.delete() } catch (_: Exception) {}
        }
        audioCache.clear()

        // Prune old temp audio files in cacheDir to keep disk footprint minimal
        scope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("eleven_") || file.name.startsWith("google_") || file.name.startsWith("fish_") || file.name.startsWith("gate_"))) {
                        if (now - file.lastModified() > 24 * 3600 * 1000L) {
                            file.delete()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Pre-loads the given sentence into the cache asynchronously in background.
     */
    fun preloadSentence(sentenceIndex: Int, rawText: String) {
        if (audioCache.containsKey(sentenceIndex) || fetchJobs.containsKey(sentenceIndex) || rawText.isBlank()) {
            return
        }

        val job = scope.launch(Dispatchers.IO) {
            try {
                val file = fetchSpeechWithAutoRotation(rawText)
                if (file != null && file.exists() && file.length() > 0) {
                    audioCache[sentenceIndex] = file
                }
            } catch (e: Exception) {
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.TTS_EVENT,
                    message = "Background preload failed for sentence $sentenceIndex: ${e.message}",
                    isError = true
                )
            } finally {
                fetchJobs.remove(sentenceIndex)
            }
        }
        fetchJobs[sentenceIndex] = job
    }

    /**
     * Pre-loads upcoming sentences (N+1, N+2) from parsed document into background cache.
     */
    fun preloadAhead(doc: ParsedDocument, currentSentenceIdx: Int) {
        for (offset in 1..3) {
            val nextIdx = currentSentenceIdx + offset
            if (nextIdx < doc.sentences.size) {
                preloadSentence(nextIdx, doc.sentences[nextIdx].text)
            }
        }
    }

    fun synthesizeAndPlay(
        sentenceIndex: Int,
        rawText: String,
        doc: ParsedDocument?,
        listener: CloudSynthesisListener
    ) {
        stopPlaybackOnly()

        scope.launch(Dispatchers.IO) {
            try {
                // Trigger lookahead buffering for upcoming sentences
                if (doc != null) {
                    preloadAhead(doc, sentenceIndex)
                }

                // Check if audio file is already cached
                var cachedFile = audioCache[sentenceIndex]

                if (cachedFile == null || !cachedFile.exists()) {
                    // Wait if a background fetch job is currently downloading it
                    val activeJob = fetchJobs[sentenceIndex]
                    activeJob?.join()
                    cachedFile = audioCache[sentenceIndex]
                }

                // If still null, fetch synchronously
                if (cachedFile == null || !cachedFile.exists()) {
                    cachedFile = fetchSpeechWithAutoRotation(rawText)
                    if (cachedFile != null) {
                        audioCache[sentenceIndex] = cachedFile
                    }
                }

                if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                    throw IllegalStateException("Failed to synthesize audio for sentence $sentenceIndex")
                }

                val nextSentenceIdx = sentenceIndex + 1
                var nextCachedFile = audioCache[nextSentenceIdx]

                withContext(Dispatchers.Main) {
                    listener.onSynthesisStart(rawText)
                    playAudioFile(cachedFile, listener)
                    if (nextCachedFile != null && nextCachedFile.exists()) {
                        prepareNextPlayer(nextCachedFile)
                    }
                }

            } catch (e: Exception) {
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.TTS_EVENT,
                    message = "Cloud voice synthesis error: ${e.message}",
                    isError = true
                )
                withContext(Dispatchers.Main) {
                    listener.onErrorAndFallback(e.message ?: "Voice synthesis unavailable")
                }
            }
        }
    }

    suspend fun fetchSpeechWithAutoRotation(rawText: String): File? {
        if (!ApiKeyManager.allowApiCall()) {
            delay(800L)
        }
        val normalizedData = TextNormalizationPipeline.processFullPipeline(rawText)
        val textToSpeak = normalizedData.coarticulatedText.ifBlank { rawText }
        val cleanText = textToSpeak.replace(Regex("<[^>]*>"), "").trim()

        // 1. Analyze tone with Gemini API
        val geminiTone = com.example.ai.GeminiEmotionalToneAnalyzer.analyzeSegmentTone(cleanText)

        // 2. Process non-lexical audio artifacts via RespiratorySynthesisManager
        val respiratoryProsody = RespiratorySynthesisManager.processSentence(cleanText, geminiTone)

        // 3. STRICT VOICE LOCK: Always use the selected character's exact Voice ID
        val activeCharacter = VoiceCharacterManager.selectedCharacter.value
        val primaryVoiceId = activeCharacter.elevenLabsVoiceId

        // Check Disk LRU Cache first for instantaneous zero-latency playback
        val cachedDiskFile = lruCacheManager.getCachedAudioFile(primaryVoiceId, respiratoryProsody.processedText)
        if (cachedDiskFile != null && cachedDiskFile.exists() && cachedDiskFile.length() > 0) {
            return AdaptiveNoiseGate.processAudioFile(cachedDiskFile, primaryVoiceId)
        }

        // Fetch fresh speech from ElevenLabs API with consistent locked Voice ID
        try {
            val file = fetchElevenLabsSpeech(
                text = respiratoryProsody.processedText,
                voiceId = primaryVoiceId,
                stability = respiratoryProsody.stability,
                similarityBoost = respiratoryProsody.similarityBoost,
                style = respiratoryProsody.style
            )
            if (file != null && file.exists() && file.length() > 0) {
                val cleanedFile = AdaptiveNoiseGate.processAudioFile(file, primaryVoiceId)
                return lruCacheManager.saveToCache(primaryVoiceId, respiratoryProsody.processedText, cleanedFile)
            }
        } catch (e: Exception) {
            TtsDiagnosticLogger.log(
                eventType = LogEventType.API_KEY_ROTATED,
                message = "Primary voice ($primaryVoiceId) fetch retry: ${e.message}"
            )
        }

        // Secondary Neural Engine: Google AI Cloud Journey & Neural2 Voices
        try {
            val googleFile = fetchGoogleNeuralSpeech(respiratoryProsody.processedText, activeCharacter.googleVoiceName)
            if (googleFile != null && googleFile.exists() && googleFile.length() > 0) {
                val cleanedFile = AdaptiveNoiseGate.processAudioFile(googleFile, primaryVoiceId)
                return lruCacheManager.saveToCache(primaryVoiceId, respiratoryProsody.processedText, cleanedFile)
            }
        } catch (e: Exception) {
            TtsDiagnosticLogger.log(
                eventType = LogEventType.API_KEY_ROTATED,
                message = "Google Neural voice fetch retry: ${e.message}"
            )
        }

        // Fallback: Fish.audio API with consistent voice parameters
        try {
            val fishFile = fetchFishAudioSpeech(respiratoryProsody.processedText)
            if (fishFile != null && fishFile.exists() && fishFile.length() > 0) {
                val cleanedFile = AdaptiveNoiseGate.processAudioFile(fishFile, primaryVoiceId)
                return lruCacheManager.saveToCache(primaryVoiceId, respiratoryProsody.processedText, cleanedFile)
            }
        } catch (e: Exception) {
            TtsDiagnosticLogger.log(
                eventType = LogEventType.API_KEY_ROTATED,
                message = "Fish.audio API fallback error: ${e.message}"
            )
        }

        throw IllegalStateException("All neural voice API candidates exhausted or quota exceeded")
    }

    private fun fetchElevenLabsSpeech(
        text: String,
        voiceId: String,
        stability: Double = 0.38,
        similarityBoost: Double = 0.85,
        style: Double = 0.35
    ): File? {
        var apiKey = ApiKeyManager.getElevenLabsKey()
        val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"

        val jsonPayload = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_turbo_v2_5")
            put("voice_settings", JSONObject().apply {
                put("stability", stability)
                put("similarity_boost", similarityBoost)
                put("style", style)
                put("use_speaker_boost", true)
            })
        }

        var attempts = 0
        while (attempts < 3) {
            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/mpeg")
                .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val audioBytes = response.body?.bytes() ?: return null
                return saveTempAudioFile(audioBytes, "eleven_")
            } else {
                val errBody = response.body?.string() ?: ""
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.API_KEY_ROTATED,
                    message = "ElevenLabs key failed with HTTP ${response.code}: $errBody",
                    isError = true
                )
                apiKey = ApiKeyManager.reportElevenLabsErrorAndRotate(apiKey, response.code, errBody)
                attempts++
            }
        }
        throw IllegalStateException("ElevenLabs key pool exhausted")
    }

    private fun fetchGoogleNeuralSpeech(text: String, googleVoiceName: String): File? {
        val apiKey = ApiKeyManager.getActiveKey()
        if (apiKey.isBlank()) return null

        val url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"

        val jsonPayload = JSONObject().apply {
            put("input", JSONObject().apply {
                put("text", text)
            })
            put("voice", JSONObject().apply {
                put("languageCode", if (googleVoiceName.startsWith("en-GB")) "en-GB" else "en-US")
                put("name", googleVoiceName)
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", "MP3")
                put("speakingRate", 1.0)
                put("pitch", 0.0)
            })
        }

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return null
                val jsonObj = JSONObject(jsonStr)
                val audioContentBase64 = jsonObj.optString("audioContent")
                if (audioContentBase64.isNotBlank()) {
                    val audioBytes = android.util.Base64.decode(audioContentBase64, android.util.Base64.DEFAULT)
                    return saveTempAudioFile(audioBytes, "google_")
                }
            } else {
                val errBody = response.body?.string() ?: ""
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.API_KEY_ROTATED,
                    message = "Google Neural TTS HTTP ${response.code}: $errBody",
                    isError = true
                )
            }
        } catch (e: Exception) {
            TtsDiagnosticLogger.log(
                eventType = LogEventType.API_KEY_ROTATED,
                message = "Google Neural TTS exception: ${e.message}",
                isError = true
            )
        }
        return null
    }

    private fun fetchFishAudioSpeech(text: String): File? {
        var apiKey = ApiKeyManager.getFishAudioKey()
        val url = "https://api.fish.audio/v1/tts"

        val jsonPayload = JSONObject().apply {
            put("text", text)
            put("format", "mp3")
            put("chunk_length", 200)
            put("latency", "normal")
        }

        var attempts = 0
        while (attempts < 3) {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val audioBytes = response.body?.bytes() ?: return null
                return saveTempAudioFile(audioBytes, "fish_")
            } else {
                val errBody = response.body?.string() ?: ""
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.API_KEY_ROTATED,
                    message = "Fish.audio key failed with HTTP ${response.code}: $errBody",
                    isError = true
                )
                apiKey = ApiKeyManager.reportFishAudioErrorAndRotate(apiKey, response.code, errBody)
                attempts++
            }
        }
        throw IllegalStateException("Fish.audio key pool exhausted")
    }

    private fun saveTempAudioFile(audioBytes: ByteArray, prefix: String): File {
        val file = File.createTempFile(prefix, ".mp3", context.cacheDir)
        FileOutputStream(file).use { fos ->
            fos.write(audioBytes)
            fos.flush()
        }
        return file
    }

    private fun playAudioFile(file: File, listener: CloudSynthesisListener) {
        stopPlaybackOnly()

        try {
            val audioSessionId = AudioSessionManager.generateUniqueAudioSessionId(context)
            mediaPlayer = AudioSessionManager.createManagedMediaPlayer(audioSessionId).apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }

            isPlayingActive = true

            val duration = mediaPlayer?.duration ?: 1

            trackingJob = scope.launch(Dispatchers.Default) {
                while (isPlayingActive && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                    val currentPos = mediaPlayer?.currentPosition ?: 0
                    val frac = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                    withContext(Dispatchers.Main) {
                        listener.onPlaybackProgress(frac, currentPos, duration)
                    }

                    delay(33L)
                }
            }

            mediaPlayer?.setOnCompletionListener {
                isPlayingActive = false
                trackingJob?.cancel()
                AudioSessionManager.releaseMediaPlayer(mediaPlayer)
                mediaPlayer = null
                listener.onPlaybackCompleted()
            }

            mediaPlayer?.setOnErrorListener { _, what, extra ->
                isPlayingActive = false
                trackingJob?.cancel()
                AudioSessionManager.releaseMediaPlayer(mediaPlayer)
                mediaPlayer = null
                listener.onErrorAndFallback("Playback error ($what, $extra)")
                true
            }

        } catch (e: Exception) {
            isPlayingActive = false
            AudioSessionManager.releaseMediaPlayer(mediaPlayer)
            mediaPlayer = null
            listener.onErrorAndFallback("Audio player initialization error: ${e.message}")
        }
    }

    fun pause() {
        if (isPlayingActive && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    /**
     * Audition / preview a character's voice sample before selecting or switching.
     */
    fun auditionVoiceSample(voiceId: String, sampleText: String, onComplete: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = fetchElevenLabsSpeech(sampleText, voiceId, 0.45, 0.85, 0.35)
                if (file != null && file.exists()) {
                    withContext(Dispatchers.Main) {
                        playAudioFile(file, object : CloudSynthesisListener {
                            override fun onSynthesisStart(text: String) {}
                            override fun onPlaybackProgress(progressFraction: Float, currentAudioPositionMs: Int, totalDurationMs: Int) {}
                            override fun onPlaybackCompleted() { onComplete() }
                            override fun onErrorAndFallback(errorMessage: String) { onComplete() }
                        })
                    }
                } else {
                    withContext(Dispatchers.Main) { onComplete() }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun resume() {
        if (isPlayingActive && mediaPlayer != null && !mediaPlayer!!.isPlaying) {
            mediaPlayer?.start()
        }
    }

    fun prepareNextPlayer(nextFile: File) {
        if (!isPlayingActive || mediaPlayer == null || !nextFile.exists()) return
        try {
            if (nextMediaPlayer == null) {
                val audioSessionId = AudioSessionManager.generateUniqueAudioSessionId(context)
                nextMediaPlayer = AudioSessionManager.createManagedMediaPlayer(audioSessionId).apply {
                    setDataSource(nextFile.absolutePath)
                    prepare()
                }
                mediaPlayer?.setNextMediaPlayer(nextMediaPlayer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopPlaybackOnly() {
        isPlayingActive = false
        trackingJob?.cancel()
        AudioSessionManager.releaseMediaPlayer(mediaPlayer)
        mediaPlayer = null
        AudioSessionManager.releaseMediaPlayer(nextMediaPlayer)
        nextMediaPlayer = null
    }

    fun stop() {
        stopPlaybackOnly()
        clearCache()
    }
}

