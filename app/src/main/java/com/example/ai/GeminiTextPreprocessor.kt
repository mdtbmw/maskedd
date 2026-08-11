package com.example.ai

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)

interface GeminiRestService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiTextPreprocessor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val service: GeminiRestService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiRestService::class.java)
    }

    private suspend fun <T> executeWithKeyRotation(block: suspend (apiKey: String) -> T): Result<T> = withContext(Dispatchers.IO) {
        var currentKey = ApiKeyManager.getActiveKey()
        if (currentKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Gemini API key available in rotation pool."))
        }

        var attempts = 0
        while (attempts < 3) {
            attempts++
            try {
                val result = block(currentKey)
                return@withContext Result.success(result)
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val nextKey = ApiKeyManager.reportKeyErrorAndRotate(currentKey, code, e.message ?: "Http Exception")
                if (nextKey != null) {
                    currentKey = nextKey
                } else {
                    return@withContext Result.failure(e)
                }
            } catch (e: Exception) {
                val nextKey = ApiKeyManager.reportKeyErrorAndRotate(currentKey, 500, e.message ?: "Unknown Exception")
                if (nextKey != null) {
                    currentKey = nextKey
                } else {
                    return@withContext Result.failure(e)
                }
            }
        }
        Result.failure(RuntimeException("Exhausted retries across Gemini API key rotation pool."))
    }

    suspend fun preprocessTextWithGemini(inputText: String): Result<String> {
        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "You are MaskedD's advanced TTS phonetics & contextual prosody engine.\n" +
                            "Clean, disambiguate homographs, normalize acronyms and numbers, apply coarticulation softening, and inject SSML micro-pause tags:\n" +
                            "1. Disambiguate homographs based on context (e.g. 'read' past -> 'red', 'lead' metal -> 'led').\n" +
                            "2. Spell acronyms ('API' -> 'A P I') and convert numbers/currency ('$50' -> 'fifty dollars', '1984' date -> 'nineteen eighty-four').\n" +
                            "3. Soften consonant boundaries ('want to' -> 'wanna', 'going to' -> 'gonna', 'butter' -> 'budder').\n" +
                            "4. Output clean SSML phonetically formatted string without commentary."
                )
            )
        )

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = inputText))
                )
            ),
            systemInstruction = systemInstruction
        )

        return executeWithKeyRotation { apiKey ->
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: throw RuntimeException("Gemini returned empty text response.")
        }
    }

    suspend fun askSocraticCoPilot(
        userQuery: String,
        currentSentenceContext: String,
        documentTitle: String
    ): Result<String> {
        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "You are a brilliant Socratic Learning Companion inside the MaskedD Book Reader. " +
                            "Your goal is to help readers comprehend, connect, and remember complex ideas easily. " +
                            "Be conversational, warm, concise, and engaging (2-4 bullet sentences max). " +
                            "Context from the current reading section:\n" +
                            "Document: '$documentTitle'\n" +
                            "Active Sentence: '$currentSentenceContext'\n\n" +
                            "If asked to explain like I'm 5 (ELI5), use vivid analogies. If asked for key takeaways, provide 3 punchy points."
                )
            )
        )

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = userQuery)))
            ),
            systemInstruction = systemInstruction
        )

        return executeWithKeyRotation { apiKey ->
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: throw RuntimeException("Empty response from Socratic Companion.")
        }
    }

    suspend fun generateActiveRecallQuiz(
        documentSnippet: String,
        title: String
    ): Result<String> {
        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "You are a cognitive memory coach. Generate 3 short multiple-choice active recall questions based on this text snippet from '$title'. " +
                            "Format the response clearly with Q1, options A/B/C/D, and indicating the correct answer."
                )
            )
        )

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = documentSnippet.take(2000))))
            ),
            systemInstruction = systemInstruction
        )

        return executeWithKeyRotation { apiKey ->
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: throw RuntimeException("Empty quiz response.")
        }
    }

    suspend fun generatePodcastDialogueScript(
        documentSnippet: String,
        title: String
    ): Result<String> {
        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "You are a professional podcast scriptwriter. Convert the following text from '$title' into an engaging, 2-host conversational podcast debate between 'Host A (Alex)' and 'Host B (Jordan)'. " +
                            "Use natural conversational dialogue, friendly banter, expressive pauses, and insightful commentary."
                )
            )
        )

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = documentSnippet.take(2500))))
            ),
            systemInstruction = systemInstruction
        )

        return executeWithKeyRotation { apiKey ->
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: throw RuntimeException("Empty podcast script response.")
        }
    }
}
