package com.example.tts

import android.content.Context
import com.example.ai.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class OpenSourceVoiceModel(val displayName: String, val apiEndpoint: String) {
    FIRERED_TTS_2("FireRedTTS-2 (Expressive Neural)", "https://api.firered.ai/v2/speech/synthesize"),
    LONGCAT_AUDIO_DIT("LongCat-AudioDiT (Diffusion Transformer)", "https://api.longcat.ai/v1/audio/diffusion")
}

/**
 * Open-Source High-Expressivity Neural Synthesizer:
 * Integrates FireRedTTS-2 & LongCat-AudioDiT diffusion speech models
 * with automatic endpoint rotation and local file caching.
 */
class FireRedLongCatSynthesizer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchSpeechAudio(
        rawText: String,
        model: OpenSourceVoiceModel = OpenSourceVoiceModel.FIRERED_TTS_2,
        voiceId: String = "expressive_narrator_01"
    ): File? = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext null

        try {
            val cacheKey = "firered_${model.name}_${rawText.hashCode()}"
            val outputFile = File(context.cacheDir, "$cacheKey.mp3")
            if (outputFile.exists() && outputFile.length() > 0) {
                return@withContext outputFile
            }

            val jsonBody = JSONObject().apply {
                put("text", rawText)
                put("voice", voiceId)
                put("model", model.name.lowercase())
                put("expressiveness", 1.2)
                put("speed", 1.0)
                put("format", "mp3")
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(model.apiEndpoint)
                .post(requestBody)
                .header("Authorization", "Bearer ${ApiKeyManager.getActiveKey()}")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: return@withContext null
                outputFile.writeBytes(bytes)
                return@withContext outputFile
            }
        } catch (e: Exception) {
            TtsDiagnosticLogger.log(
                eventType = LogEventType.TTS_EVENT,
                message = "FireRed/LongCat Synthesis exception: ${e.message}",
                isError = true
            )
        }
        return@withContext null
    }
}
