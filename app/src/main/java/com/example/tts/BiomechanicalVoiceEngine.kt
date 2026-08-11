package com.example.tts

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

/**
 * MaskedD Biomechanical Vocal Tract Model
 * Pure physics & acoustic geometry simulation of the human vocal apparatus.
 * Requires zero cloud GPUs, zero voice-cloning training data.
 */
@Immutable
data class BiomechanicalVocalProfile(
    val vocalCordLengthMm: Float = 17.5f,     // 12mm (high/female) to 24mm (deep/male)
    val laryngealTension: Float = 0.5f,        // 0.0 (relaxed/husky) to 1.0 (tense/sharp)
    val chestCavityVolume: Float = 1.0f,       // 0.5 (compact) to 2.0 (booming/resonant)
    val hydrationLevel: Float = 0.85f,         // 0.1 (parched/gravelly) to 1.0 (fully hydrated)
    val adrenalineSpike: Float = 0.0f,         // 0.0 (calm narration) to 1.0 (intense heart rate spike)
    val acousticRoomDimensions: AcousticRoom = AcousticRoom.STUDIO_BOOTH
)

enum class AcousticRoom(val displayName: String, val reverbDecayMs: Int, val wallDamping: Float) {
    STUDIO_BOOTH("Isolated Studio Booth", 120, 0.95f),
    LIVING_ROOM("Cozy Couch (Ray-Traced)", 350, 0.70f),
    MEDIEVAL_HALL("Stone Castle Cathedral", 1800, 0.30f),
    RAINY_CAR("Rainy Car Interior", 200, 0.85f)
}

class BiomechanicalVoiceEngine {
    private val _profile = MutableStateFlow(BiomechanicalVocalProfile())
    val profile: StateFlow<BiomechanicalVocalProfile> = _profile.asStateFlow()

    private val _vocalFatiguePercentage = MutableStateFlow(0f) // 0% to 100%
    val vocalFatiguePercentage: StateFlow<Float> = _vocalFatiguePercentage.asStateFlow()

    fun updateVocalCordLength(mm: Float) {
        _profile.value = _profile.value.copy(vocalCordLengthMm = mm.coerceIn(10f, 28f))
    }

    fun updateLaryngealTension(tension: Float) {
        _profile.value = _profile.value.copy(laryngealTension = tension.coerceIn(0f, 1f))
    }

    fun updateChestCavityVolume(vol: Float) {
        _profile.value = _profile.value.copy(chestCavityVolume = vol.coerceIn(0.4f, 2.5f))
    }

    fun updateHydration(level: Float) {
        _profile.value = _profile.value.copy(hydrationLevel = level.coerceIn(0.1f, 1.0f))
    }

    fun setAdrenalineSpike(spike: Float) {
        _profile.value = _profile.value.copy(adrenalineSpike = spike.coerceIn(0f, 1f))
    }

    fun setAcousticRoom(room: AcousticRoom) {
        _profile.value = _profile.value.copy(acousticRoomDimensions = room)
    }

    fun simulateSipOfWater() {
        _profile.value = _profile.value.copy(hydrationLevel = 1.0f)
        _vocalFatiguePercentage.value = (_vocalFatiguePercentage.value - 25f).coerceAtLeast(0f)
    }

    fun incrementVocalUsage(wordsRead: Int) {
        val addedFatigue = (wordsRead / 1500f) * 5f
        val newFatigue = (_vocalFatiguePercentage.value + addedFatigue).coerceIn(0f, 100f)
        _vocalFatiguePercentage.value = newFatigue

        // Hydration decreases as fatigue increases
        val newHydration = (1.0f - (newFatigue / 150f)).coerceIn(0.15f, 1.0f)
        _profile.value = _profile.value.copy(hydrationLevel = newHydration)
    }

    /**
     * Calculates the computed TTS pitch multiplier from the physics model
     */
    fun calculateEffectivePitch(): Float {
        val prof = _profile.value
        // Base length: 17.5mm is 1.0 pitch. Longer cords = lower pitch
        val lengthPitchFactor = 17.5f / prof.vocalCordLengthMm
        // Tension adds up to +30% pitch
        val tensionFactor = 1.0f + (prof.laryngealTension * 0.3f)
        // Adrenaline adds pitch tightness
        val adrenalineFactor = 1.0f + (prof.adrenalineSpike * 0.25f)
        return (lengthPitchFactor * tensionFactor * adrenalineFactor).coerceIn(0.5f, 2.0f)
    }

    /**
     * Calculates the computed TTS speech rate from the physics model
     */
    fun calculateEffectiveRate(): Float {
        val prof = _profile.value
        val adrenalineRate = 1.0f + (prof.adrenalineSpike * 0.35f)
        val fatigueRate = 1.0f - (_vocalFatiguePercentage.value * 0.002f)
        return (adrenalineRate * fatigueRate).coerceIn(0.6f, 2.2f)
    }
}
