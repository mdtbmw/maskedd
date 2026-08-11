package com.example.tts

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class VoiceCharacter(
    val id: String,
    val name: String,
    val description: String,
    val tag: String,
    val avatarEmoji: String,
    val accentRegion: String,
    val elevenLabsVoiceId: String,
    val googleVoiceName: String = "en-US-Journey-F",
    val defaultSpeed: Float = 1.0f,
    val defaultPitch: Float = 1.0f,
    val expressivePersonalityPrompt: String = "Warm human narration with natural pauses and chuckles",
    val isFavorite: Boolean = false,
    val isCustomCloned: Boolean = false
)

object VoiceCharacterManager {

    private val defaultCharacters = listOf(
        VoiceCharacter(
            id = "amina_ng",
            name = "Amina",
            description = "Warm, rhythmic West African storyteller with natural, engaging cadence and soft laughs",
            tag = "Nigerian Accent Reader",
            avatarEmoji = "🇳🇬",
            accentRegion = "Nigeria",
            elevenLabsVoiceId = "21m00Tcm4TlvDq8ikWAM",
            expressivePersonalityPrompt = "Nigerian accent, warm melodic rhythm, gentle laughs on lighthearted sentences",
            isFavorite = true
        ),
        VoiceCharacter(
            id = "kofi_ng",
            name = "Kofi",
            description = "Rich, resonant West African male narrator with deep, authoritative storytelling warmth",
            tag = "Nigerian / African Male",
            avatarEmoji = "🇳🇬",
            accentRegion = "Nigeria",
            elevenLabsVoiceId = "ErXwobaYiN019PkySvjV",
            expressivePersonalityPrompt = "Deep West African male accent, expressive pauses, authentic cadence",
            isFavorite = true
        ),
        VoiceCharacter(
            id = "leo_kid",
            name = "Leo",
            description = "Enthusiastic, curious kid reader with bright giggles and playful curiosity",
            tag = "Playful Kid Reader",
            avatarEmoji = "🧒",
            accentRegion = "Global Youth",
            elevenLabsVoiceId = "AZnzlk1XvdvUeBnXmlld",
            defaultSpeed = 1.05f,
            defaultPitch = 1.15f,
            expressivePersonalityPrompt = "Youthful kid voice, lively chuckles, high energy exclamation reading",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "rachel_classic",
            name = "Rachel",
            description = "Smooth, conversational female narrator with natural human warmth & soft inhales",
            tag = "Classic Storyteller",
            avatarEmoji = "🎙️",
            accentRegion = "US / English",
            elevenLabsVoiceId = "21m00Tcm4TlvDq8ikWAM",
            expressivePersonalityPrompt = "Warm human narration, subtle diaphragm breaths, smooth transitions",
            isFavorite = true
        ),
        VoiceCharacter(
            id = "domi_articulate",
            name = "Domi",
            description = "Crisp, fast-paced articulate reader ideal for technical papers and fast reading",
            tag = "Dynamic & Crisp",
            avatarEmoji = "⚡",
            accentRegion = "US / English",
            elevenLabsVoiceId = "AZnzlk1XvdvUeBnXmlld",
            defaultSpeed = 1.1f,
            expressivePersonalityPrompt = "Clear crisp diction, minimal hesitations, energetic flow",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "bella_whimsical",
            name = "Bella",
            description = "Soft, expressive voice with gentle giggles and expressive emotional inflection",
            tag = "Whimsical & Soft",
            avatarEmoji = "🌸",
            accentRegion = "US / English",
            elevenLabsVoiceId = "EXAVITQu4vr4xnSDxMaL",
            expressivePersonalityPrompt = "Soft gentle voice, quiet giggles at humorous lines, airy breath",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "antoni_socratic",
            name = "Marcus",
            description = "Thoughtful, academic male reader with intellectual depth, deliberate pauses and Socratic poise",
            tag = "Deep Socratic Scholar",
            avatarEmoji = "📚",
            accentRegion = "UK / Academic",
            elevenLabsVoiceId = "ErXwobaYiN019PkySvjV",
            defaultSpeed = 0.92f,
            expressivePersonalityPrompt = "Deep philosophical tone, thoughtful pauses, subtle chuckles",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "elli_youthful",
            name = "Elli",
            description = "Modern, casual teenage reader with natural speech rhythm and friendly vibe",
            tag = "Youthful Reader",
            avatarEmoji = "✨",
            accentRegion = "US Youth",
            elevenLabsVoiceId = "MF3mGyEYCl7XYWbV9V6O",
            expressivePersonalityPrompt = "Casual conversational tone, energetic phrasing",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "josh_bard",
            name = "Zayn",
            description = "Deep, cinematic storytelling voice crafted for fantasy, fiction and epic audiobooks",
            tag = "Dramatic Fantasy Bard",
            avatarEmoji = "🎭",
            accentRegion = "Global Narrative",
            elevenLabsVoiceId = "TxGEqnHWrfWFTfGW9XjX",
            expressivePersonalityPrompt = "Dramatic cinematic voice, deep suspenseful pauses, intense energy",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "arnold_cinematic",
            name = "Arnold",
            description = "Authoritative, powerful narrator voice with rich low frequencies and epic gravity",
            tag = "Bold Narrator",
            avatarEmoji = "🦁",
            accentRegion = "US / Deep",
            elevenLabsVoiceId = "VR6AewLTigWG4xT3OH44",
            defaultSpeed = 0.88f,
            expressivePersonalityPrompt = "Gravitas narration, deep resonant diaphragm, heavy emphasis",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "adam_daily",
            name = "Adam",
            description = "Warm, relaxed everyday reader for news articles and casual reading",
            tag = "Casual Daily Reader",
            avatarEmoji = "☕",
            accentRegion = "US / Standard",
            elevenLabsVoiceId = "pNInz6obpgDQGcFmaJgB",
            expressivePersonalityPrompt = "Balanced newsroom narration, crisp clear cadence",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "maya_meditation",
            name = "Maya",
            description = "Airy, peaceful guide with gentle exhalations, soft whispers, and soothing pace",
            tag = "Lo-Fi Meditation",
            avatarEmoji = "🧘",
            accentRegion = "Global Peaceful",
            elevenLabsVoiceId = "EXAVITQu4vr4xnSDxMaL",
            defaultSpeed = 0.85f,
            expressivePersonalityPrompt = "Soft whispers, soothing exhalations, gentle unhurried rhythm",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "saffron_afro",
            name = "Saffron",
            description = "Vibrant, rhythmic global narrator with expressive Afro-fusion storytelling flair",
            tag = "Afro-Fusion Modern",
            avatarEmoji = "🌍",
            accentRegion = "Global Afro-Fusion",
            elevenLabsVoiceId = "21m00Tcm4TlvDq8ikWAM",
            expressivePersonalityPrompt = "Afro-fusion rhythmic inflection, joyful chuckles, vivid expression",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "aria_cyber",
            name = "Aria",
            description = "Polished, futuristic narrator with clean articulation for sci-fi and tech",
            tag = "Cyberpunk / Sci-Fi",
            avatarEmoji = "🔮",
            accentRegion = "Futuristic",
            elevenLabsVoiceId = "AZnzlk1XvdvUeBnXmlld",
            expressivePersonalityPrompt = "Clean synth precision, smooth articulation",
            isFavorite = false
        ),
        VoiceCharacter(
            id = "oliver_british",
            name = "Oliver",
            description = "Refined, elegant literary narrator for classic novels and historical texts",
            tag = "British Master Reader",
            avatarEmoji = "🏰",
            accentRegion = "British RP",
            elevenLabsVoiceId = "TxGEqnHWrfWFTfGW9XjX",
            defaultSpeed = 0.95f,
            expressivePersonalityPrompt = "Classic British eloquence, formal cadence, rich literary depth",
            isFavorite = false
        )
    )

    private val _characters = MutableStateFlow<List<VoiceCharacter>>(defaultCharacters)
    val characters: StateFlow<List<VoiceCharacter>> = _characters.asStateFlow()

    private val _selectedCharacter = MutableStateFlow<VoiceCharacter>(defaultCharacters.first())
    val selectedCharacter: StateFlow<VoiceCharacter> = _selectedCharacter.asStateFlow()

    fun selectCharacter(characterId: String) {
        val found = _characters.value.find { it.id == characterId } ?: return
        _selectedCharacter.value = found
    }

    fun toggleFavorite(characterId: String) {
        _characters.value = _characters.value.map { char ->
            if (char.id == characterId) {
                char.copy(isFavorite = !char.isFavorite)
            } else {
                char
            }
        }
        if (_selectedCharacter.value.id == characterId) {
            _selectedCharacter.value = _selectedCharacter.value.copy(
                isFavorite = !_selectedCharacter.value.isFavorite
            )
        }
    }

    fun addCustomClonedVoice(
        name: String,
        description: String,
        tag: String,
        avatarEmoji: String,
        elevenLabsVoiceId: String
    ): VoiceCharacter {
        val customId = "custom_${System.currentTimeMillis()}"
        val newChar = VoiceCharacter(
            id = customId,
            name = name.ifBlank { "My Voice Clone" },
            description = description.ifBlank { "User custom cloned AI voice profile" },
            tag = tag.ifBlank { "Custom Clone" },
            avatarEmoji = avatarEmoji.ifBlank { "🎙️" },
            accentRegion = "Custom Profile",
            elevenLabsVoiceId = elevenLabsVoiceId.ifBlank { "21m00Tcm4TlvDq8ikWAM" },
            isFavorite = true,
            isCustomCloned = true
        )
        _characters.value = listOf(newChar) + _characters.value
        _selectedCharacter.value = newChar
        return newChar
    }
}
