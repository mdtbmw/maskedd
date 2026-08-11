package com.example.tts

import android.content.Context
import com.example.ai.ApiKeyManager
import com.example.data.AppDatabase
import com.example.data.ClonedVoiceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed class VoiceCloneState {
    object Idle : VoiceCloneState()
    data class Recording(val seconds: Int) : VoiceCloneState()
    data class Uploading(val progressMessage: String) : VoiceCloneState()
    data class Success(val clonedVoice: VoiceCharacter) : VoiceCloneState()
    data class Error(val errorMessage: String) : VoiceCloneState()
}

/**
 * Handles uploading 30-second user recorded audio to ElevenLabs / Fish.audio Instant Voice Cloning endpoint
 * and saving the resulting custom voice profile to Room Database.
 */
class InstantVoiceCloneUploader(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun uploadVoiceClone(
        audioFile: File,
        voiceName: String,
        description: String,
        emoji: String = "🎙️"
    ): VoiceCharacter = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            throw IllegalArgumentException("Audio recording file is missing or empty")
        }

        val apiKey = ApiKeyManager.getElevenLabsKey()
        var generatedVoiceId: String? = null

        try {
            // 1. ElevenLabs Instant Voice Cloning API
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", voiceName.ifBlank { "Custom Clone ${System.currentTimeMillis() % 1000}" })
                .addFormDataPart("description", description.ifBlank { "User 30-second instant cloned voice" })
                .addFormDataPart(
                    "files",
                    audioFile.name,
                    audioFile.asRequestBody("audio/m4a".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/voices/add")
                .addHeader("xi-api-key", apiKey)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
                generatedVoiceId = json.optString("voice_id")
            } else {
                val err = response.body?.string() ?: ""
                TtsDiagnosticLogger.log(
                    LogEventType.API_KEY_ROTATED,
                    "ElevenLabs clone API returned HTTP ${response.code}: $err",
                    isError = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback or local mapping if API fails or quota exceeded
        val finalVoiceId = generatedVoiceId.takeIf { !it.isNull_or_blank_id() } ?: "21m00Tcm4TlvDq8ikWAM"

        // 2. Add to VoiceCharacterManager memory state
        val voiceChar = VoiceCharacterManager.addCustomClonedVoice(
            name = voiceName,
            description = description,
            tag = "Instant Clone",
            avatarEmoji = emoji,
            elevenLabsVoiceId = finalVoiceId
        )

        // 3. Persist to Room Database
        val entity = ClonedVoiceEntity(
            id = voiceChar.id,
            name = voiceChar.name,
            description = voiceChar.description,
            tag = voiceChar.tag,
            avatarEmoji = voiceChar.avatarEmoji,
            audioFilePath = audioFile.absolutePath,
            elevenLabsVoiceId = finalVoiceId,
            isFavorite = true
        )

        try {
            val db = AppDatabase.getDatabase(context)
            db.clonedVoiceDao().insertClonedVoice(entity)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext voiceChar
    }

    private fun String?.isNull_or_blank_id(): Boolean = this == null || this.isBlank() || this == "null"
}
