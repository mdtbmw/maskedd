package com.example.ai

import com.example.BuildConfig
import com.example.tts.ProsodyMarkup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiToneResult(
    val toneName: String,
    val stability: Double,
    val style: Double,
    val similarityBoost: Double,
    val nonLexicalArtifact: String? = null
)

/**
 * Integrates with Gemini API (gemini-3.5-flash) to analyze document segments
 * for emotional tone (e.g., 'joyful', 'sarcastic', 'tense', 'dramatic')
 * and returns continuous voice settings for expressive synthesis
 * WITHOUT changing the selected voice ID.
 */
object GeminiEmotionalToneAnalyzer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeSegmentTone(segmentText: String): GeminiToneResult = withContext(Dispatchers.IO) {
        val apiKey = ApiKeyManager.getActiveKey()
        if (apiKey.isBlank() || segmentText.isBlank()) {
            return@withContext fallbackKeywordAnalysis(segmentText)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val prompt = """
                Analyze the following text segment for emotional narration tone. 
                Return JSON only matching this schema:
                {
                  "toneName": "joyful" | "sarcastic" | "tense" | "dramatic" | "contemplative" | "neutral",
                  "stability": float between 0.20 and 0.70 (lower = more expressive/variable),
                  "style": float between 0.10 and 0.60 (higher = more stylized),
                  "similarityBoost": float between 0.75 and 0.95,
                  "nonLexicalArtifact": "inhalation" | "exhale" | "chuckle" | null
                }
                Text: "$segmentText"
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respStr = response.body?.string() ?: ""
                val respJson = JSONObject(respStr)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    val textOut = parts.getJSONObject(0).optString("text", "")
                    val parsed = JSONObject(textOut)
                    return@withContext GeminiToneResult(
                        toneName = parsed.optString("toneName", "neutral"),
                        stability = parsed.optDouble("stability", 0.38),
                        style = parsed.optDouble("style", 0.35),
                        similarityBoost = parsed.optDouble("similarityBoost", 0.85),
                        nonLexicalArtifact = if (parsed.isNull("nonLexicalArtifact")) null else parsed.optString("nonLexicalArtifact")
                    )
                }
            }
        } catch (_: Exception) {
            // Fallback gracefully on network error or quota limits
        }

        return@withContext fallbackKeywordAnalysis(segmentText)
    }

    private fun fallbackKeywordAnalysis(text: String): GeminiToneResult {
        val lower = text.lowercase()
        return when {
            lower.contains("excited") || lower.contains("happy") || lower.contains("joy") || lower.contains("laugh") ->
                GeminiToneResult("joyful", stability = 0.25, style = 0.55, similarityBoost = 0.85, nonLexicalArtifact = "chuckle")
            lower.contains("whisper") || lower.contains("secret") || lower.contains("quiet") ->
                GeminiToneResult("contemplative", stability = 0.50, style = 0.45, similarityBoost = 0.90, nonLexicalArtifact = "exhale")
            lower.contains("suddenly") || lower.contains("danger") || lower.contains("fear") || lower.contains("gasp") ->
                GeminiToneResult("tense", stability = 0.28, style = 0.50, similarityBoost = 0.85, nonLexicalArtifact = "inhalation")
            lower.contains("sarcastic") || lower.contains("ironic") || lower.contains("yeah right") ->
                GeminiToneResult("sarcastic", stability = 0.30, style = 0.60, similarityBoost = 0.80)
            else ->
                GeminiToneResult("neutral", stability = 0.38, style = 0.35, similarityBoost = 0.85)
        }
    }
}
