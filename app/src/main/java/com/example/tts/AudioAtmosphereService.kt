package com.example.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AtmosphereTheme(val displayName: String, val description: String, val defaultTrack: AmbientTrack) {
    ACTION("Action", "High-energy driving beats and cinematic synth pulse for intense stories", AmbientTrack.ACTION_CINEMATIC),
    AMBIENT("Ambient", "Soothing Lofi piano and binaural ocean waves for gentle reading", AmbientTrack.LOFI_PIANO),
    FOCUS("Focus", "Harmonic low-pass drone for deep concentration and study", AmbientTrack.DEEP_FOCUS),
    OFF("Off", "Clean speech narration without ambient instrumental background", AmbientTrack.OFF)
}

/**
 * Audio Atmosphere Service managing ambient instrumental library, theme synchronization,
 * and smooth cross-fading audio transitions.
 */
class AudioAtmosphereService(
    private val scope: CoroutineScope,
    private val ambientMusicPlayer: AmbientMusicPlayer
) {
    private val _currentTheme = MutableStateFlow(AtmosphereTheme.OFF)
    val currentTheme: StateFlow<AtmosphereTheme> = _currentTheme.asStateFlow()

    fun setTheme(theme: AtmosphereTheme) {
        _currentTheme.value = theme
        if (theme == AtmosphereTheme.OFF) {
            ambientMusicPlayer.setTrack(AmbientTrack.OFF)
        } else {
            ambientMusicPlayer.setTrackWithCrossfade(theme.defaultTrack)
        }
    }

    fun setSpecificTrack(track: AmbientTrack) {
        // Find matching theme or custom track selection
        val matchingTheme = AtmosphereTheme.entries.find { it.defaultTrack == track } ?: AtmosphereTheme.AMBIENT
        _currentTheme.value = matchingTheme
        ambientMusicPlayer.setTrackWithCrossfade(track)
    }

    fun syncWithReadingMood(mode: ReadingMode) {
        val mappedTheme = when (mode) {
            ReadingMode.NEWS_ANCHOR, ReadingMode.FAST_READER -> AtmosphereTheme.ACTION
            ReadingMode.EDUCATOR, ReadingMode.MONOTONE -> AtmosphereTheme.FOCUS
            ReadingMode.BEDTIME_SOOTHE, ReadingMode.STORYTELLER -> AtmosphereTheme.AMBIENT
            else -> AtmosphereTheme.AMBIENT
        }
        setTheme(mappedTheme)
    }
}
