package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.tts.LogEventType
import com.example.tts.TtsDiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

enum class KeyStatus {
    ACTIVE,
    QUOTA_EXCEEDED,
    INVALID,
    UNTESTED
}

data class ApiKeyInfo(
    val key: String,
    val label: String,
    val status: KeyStatus = KeyStatus.UNTESTED,
    val usageCount: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val lastError: String? = null
)

/**
 * Thread-Safe Multi-Provider API Key Rotation Pool.
 * Maintains queues of 10-15+ API keys for Fish.audio, ElevenLabs, and Gemini models.
 * Automatically rotates seamlessly when rate limits (HTTP 429), payment required (HTTP 402), or network errors occur.
 */
object ApiKeyManager {

    private const val PREFS_NAME = "masked_d_api_keys_pref_v2"
    private const val KEY_FISH_KEYS = "fish_audio_keys_pool"
    private const val KEY_ELEVEN_KEYS = "elevenlabs_keys_pool"
    private const val KEY_GEMINI_KEYS = "gemini_keys_pool"

    private var prefs: SharedPreferences? = null

    // Thread-safe key pools
    private val fishAudioKeysPool = CopyOnWriteArrayList<ApiKeyInfo>()
    private val elevenLabsKeysPool = CopyOnWriteArrayList<ApiKeyInfo>()
    private val geminiKeysPool = CopyOnWriteArrayList<ApiKeyInfo>()

    private val fishIndex = AtomicInteger(0)
    private val elevenIndex = AtomicInteger(0)
    private val geminiIndex = AtomicInteger(0)

    private val _keysState = MutableStateFlow<List<ApiKeyInfo>>(emptyList())
    val keysState: StateFlow<List<ApiKeyInfo>> = _keysState.asStateFlow()

    // Dynamic key pools loaded from BuildConfig and user configuration
    private val defaultFishKeys: List<String>
        get() = try {
            val key = BuildConfig::class.java.getField("FISH_AUDIO_API_KEY").get(null) as? String
            if (!key.isNullOrBlank() && key != "null") key.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        } catch (_: Exception) { emptyList() }

    private val defaultElevenKeys: List<String>
        get() = try {
            val key = BuildConfig::class.java.getField("ELEVENLABS_API_KEY").get(null) as? String
            if (!key.isNullOrBlank() && key != "null") key.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        } catch (_: Exception) { emptyList() }

    private val defaultGeminiKeys: List<String>
        get() = try {
            val key = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            if (!key.isNullOrBlank() && key != "null" && key != "DEFAULT_API_KEY") key.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        } catch (_: Exception) { emptyList() }


    @Synchronized
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        loadPool(KEY_FISH_KEYS, defaultFishKeys, fishAudioKeysPool, "Fish.audio")
        loadPool(KEY_ELEVEN_KEYS, defaultElevenKeys, elevenLabsKeysPool, "ElevenLabs")
        loadPool(KEY_GEMINI_KEYS, defaultGeminiKeys, geminiKeysPool, "Gemini")

        _keysState.value = geminiKeysPool.toList()

        TtsDiagnosticLogger.log(
            eventType = LogEventType.API_KEY_ROTATED,
            message = "ApiKeyManager initialized: Fish.audio (${fishAudioKeysPool.size} keys), ElevenLabs (${elevenLabsKeysPool.size} keys), Gemini (${geminiKeysPool.size} keys)"
        )
    }

    private fun loadPool(
        prefKey: String,
        defaults: List<String>,
        poolTarget: CopyOnWriteArrayList<ApiKeyInfo>,
        providerName: String
    ) {
        poolTarget.clear()
        val savedSet = prefs?.getStringSet(prefKey, emptySet()) ?: emptySet()
        val combinedKeys = (savedSet + defaults.filter { it.isNotBlank() && !it.contains("MY_") }).distinct()

        for ((idx, k) in combinedKeys.withIndex()) {
            poolTarget.add(
                ApiKeyInfo(
                    key = k,
                    label = "$providerName Key #${idx + 1}",
                    status = KeyStatus.ACTIVE
                )
            )
        }
    }

    /**
     * Gets current active Fish.audio key.
     */
    fun getFishAudioKey(): String {
        if (fishAudioKeysPool.isEmpty()) return defaultFishKeys.firstOrNull() ?: ""
        val idx = fishIndex.get().coerceIn(0, fishAudioKeysPool.size - 1)
        return fishAudioKeysPool[idx].key
    }

    /**
     * Report an error on a Fish.audio key and rotate to next key in pool.
     */
    @Synchronized
    fun reportFishAudioErrorAndRotate(failedKey: String, errorCode: Int, errorMsg: String): String {
        return rotatePool(fishAudioKeysPool, fishIndex, failedKey, errorCode, errorMsg, "Fish.audio")
    }

    /**
     * Gets current active ElevenLabs key.
     */
    fun getElevenLabsKey(): String {
        if (elevenLabsKeysPool.isEmpty()) return defaultElevenKeys.firstOrNull() ?: ""
        val idx = elevenIndex.get().coerceIn(0, elevenLabsKeysPool.size - 1)
        return elevenLabsKeysPool[idx].key
    }

    /**
     * Report an error on an ElevenLabs key and rotate to next key in pool.
     */
    @Synchronized
    fun reportElevenLabsErrorAndRotate(failedKey: String, errorCode: Int, errorMsg: String): String {
        return rotatePool(elevenLabsKeysPool, elevenIndex, failedKey, errorCode, errorMsg, "ElevenLabs")
    }

    /**
     * Gets current active Gemini key.
     */
    fun getActiveKey(): String {
        if (geminiKeysPool.isEmpty()) return defaultGeminiKeys.firstOrNull() ?: ""
        val idx = geminiIndex.get().coerceIn(0, geminiKeysPool.size - 1)
        return geminiKeysPool[idx].key
    }

    /**
     * Report Gemini key error and rotate.
     */
    @Synchronized
    fun reportKeyErrorAndRotate(failedKey: String, errorCode: Int, errorMessage: String): String? {
        val nextKey = rotatePool(geminiKeysPool, geminiIndex, failedKey, errorCode, errorMessage, "Gemini")
        _keysState.value = geminiKeysPool.toList()
        return nextKey
    }

    private fun rotatePool(
        pool: CopyOnWriteArrayList<ApiKeyInfo>,
        indexCounter: AtomicInteger,
        failedKey: String,
        errorCode: Int,
        errorMessage: String,
        providerName: String
    ): String {
        if (pool.isEmpty()) return ""

        val failedIdx = pool.indexOfFirst { it.key == failedKey }
        if (failedIdx != -1) {
            val status = if (errorCode == 429 || errorCode == 402) KeyStatus.QUOTA_EXCEEDED else KeyStatus.INVALID
            pool[failedIdx] = pool[failedIdx].copy(
                status = status,
                lastError = "HTTP $errorCode: $errorMessage"
            )
        }

        // Find next active/untested key
        val currentIdx = indexCounter.get()
        var nextIdx = (currentIdx + 1) % pool.size
        var attempts = 0

        while (attempts < pool.size) {
            if (pool[nextIdx].status != KeyStatus.QUOTA_EXCEEDED && pool[nextIdx].status != KeyStatus.INVALID) {
                indexCounter.set(nextIdx)
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.API_KEY_ROTATED,
                    message = "Rotated $providerName key to index $nextIdx ('${pool[nextIdx].label}') following HTTP $errorCode"
                )
                return pool[nextIdx].key
            }
            nextIdx = (nextIdx + 1) % pool.size
            attempts++
        }

        // If all keys in pool are marked quota exceeded, reset pool statuses so round-robin continues
        for (i in 0 until pool.size) {
            pool[i] = pool[i].copy(status = KeyStatus.UNTESTED)
        }
        val resetIdx = (currentIdx + 1) % pool.size
        indexCounter.set(resetIdx)
        TtsDiagnosticLogger.log(
            eventType = LogEventType.API_KEY_ROTATED,
            message = "All $providerName keys exhausted; reset statuses and rotated to key index $resetIdx."
        )
        return pool[resetIdx].key
    }

    fun resetAllKeyStatuses() {
        for (pool in listOf(fishAudioKeysPool, elevenLabsKeysPool, geminiKeysPool)) {
            for (i in 0 until pool.size) {
                pool[i] = pool[i].copy(status = KeyStatus.UNTESTED, lastError = null)
            }
        }
        fishIndex.set(0)
        elevenIndex.set(0)
        geminiIndex.set(0)
        _keysState.value = geminiKeysPool.toList()
    }

    fun setFishAudioKey(key: String) {
        if (key.isBlank()) return
        val existingIdx = fishAudioKeysPool.indexOfFirst { it.key == key }
        if (existingIdx >= 0) {
            fishIndex.set(existingIdx)
        } else {
            fishAudioKeysPool.add(0, ApiKeyInfo(key, "Custom Fish Key", KeyStatus.ACTIVE))
            fishIndex.set(0)
        }
    }

    fun setElevenLabsKey(key: String) {
        if (key.isBlank()) return
        val existingIdx = elevenLabsKeysPool.indexOfFirst { it.key == key }
        if (existingIdx >= 0) {
            elevenIndex.set(existingIdx)
        } else {
            elevenLabsKeysPool.add(0, ApiKeyInfo(key, "Custom Eleven Key", KeyStatus.ACTIVE))
            elevenIndex.set(0)
        }
    }

    // Thread-safe rate limiter to prevent API abuse/scraping (max 60 calls per 60 seconds)
    private val requestTimestamps = java.util.concurrent.ConcurrentLinkedQueue<Long>()
    private const val MAX_REQUESTS_PER_MINUTE = 60
    private const val ONE_MINUTE_MS = 60_000L

    @Synchronized
    fun allowApiCall(): Boolean {
        val now = System.currentTimeMillis()
        while (!requestTimestamps.isEmpty() && (requestTimestamps.peek() ?: 0L) < now - ONE_MINUTE_MS) {
            requestTimestamps.poll()
        }
        if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
            TtsDiagnosticLogger.log(
                eventType = LogEventType.TTS_EVENT,
                message = "Anti-abuse rate limit triggered (${requestTimestamps.size} req/min). Auto-throttling request.",
                isError = true
            )
            return false
        }
        requestTimestamps.add(now)
        return true
    }
}
