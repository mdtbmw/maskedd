package com.example.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class AmbientTrack(val displayName: String, val description: String) {
    OFF("Muted (Speech Only)", "No background instrumental music"),
    ALPHA_10HZ("10Hz Alpha Waves (Focus)", "Binaural beat (200Hz/210Hz) for relaxed deep learning"),
    THETA_6HZ("6Hz Theta Waves (Memory)", "Binaural beat (150Hz/156Hz) for long-term retention & flow"),
    GAMMA_40HZ("40Hz Gamma (ADHD Peak)", "Binaural beat (240Hz/280Hz) for ultra-sharp concentration"),
    SOLFEGGIO_432HZ("432Hz Sacred Resonance", "Solfeggio harmonic tone for stress-free calm reading"),
    ZEN_BOWLS("Zen Garden Bowls", "Calming singing bowl harmonics & meditation tones"),
    LOFI_PIANO("Lofi Ambient Piano", "Warm electric piano chords & soft room resonance"),
    OCEAN_RAIN("Ocean Waves & Rain", "Soothing organic acoustic waves & binaural rain"),
    DEEP_FOCUS("Deep Focus Drone", "Rich harmonic pad drone for deep reading concentration"),
    ACTION_CINEMATIC("Action & Cinematic Beats", "Driving percussion & orchestral action pulse")
}

class AmbientMusicPlayer(private val scope: CoroutineScope) {

    private val _currentTrack = MutableStateFlow(AmbientTrack.OFF)
    val currentTrack: StateFlow<AmbientTrack> = _currentTrack.asStateFlow()

    private val _volume = MutableStateFlow(0.70f) // Loud default background volume (70%)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _lowPassRatio = MutableStateFlow(0.65f) // High clarity audio filter
    val lowPassRatio: StateFlow<Float> = _lowPassRatio.asStateFlow()

    private val _isTtsActive = MutableStateFlow(false)
    val isTtsActive: StateFlow<Boolean> = _isTtsActive.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null

    private val sampleRate = 22050 // Efficient 22.05kHz PCM audio synthesis

    private var targetTrack = AmbientTrack.OFF
    private var crossfadeProgress = 1.0f // 1.0 = fully transitioned to currentTrack

    fun setTrack(track: AmbientTrack) {
        setTrackWithCrossfade(track)
    }

    fun setTrackWithCrossfade(newTrack: AmbientTrack) {
        if (_currentTrack.value == newTrack) return
        targetTrack = newTrack
        _currentTrack.value = newTrack
        if (newTrack == AmbientTrack.OFF) {
            stop()
        } else {
            crossfadeProgress = 0.0f
            if (synthJob == null || synthJob?.isActive == false) {
                start()
            }
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
    }

    fun setLowPassRatio(ratio: Float) {
        _lowPassRatio.value = ratio.coerceIn(0.05f, 1.0f)
    }

    fun setTtsActive(active: Boolean) {
        _isTtsActive.value = active
    }

    fun start() {
        if (_currentTrack.value == AmbientTrack.OFF) return
        if (synthJob?.isActive == true && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            return // Audio track is already running smoothly, no need to recreate
        }

        stop()

        synthJob = scope.launch(Dispatchers.Default) {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 8).coerceAtLeast(32768)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val pcmBuffer = ShortArray(2048)
            var sampleIndex = 0L

            var previousFilteredSample = 0.0
            var currentGain = _volume.value
            var noiseSeed = 123456789L

            fun fastNoise(): Double {
                noiseSeed = (noiseSeed * 1664525L + 1013904223L) and 0xFFFFFFFFL
                return (noiseSeed.toDouble() / 4294967295.0) * 2.0 - 1.0
            }

            // Chord frequencies in Hz (Cmaj9, Am9, Fmaj7, G6)
            val chords = when (_currentTrack.value) {
                AmbientTrack.ZEN_BOWLS -> listOf(
                    doubleArrayOf(261.63, 329.63, 392.00, 493.88), // Cmaj7
                    doubleArrayOf(220.00, 261.63, 329.63, 392.00), // Am7
                    doubleArrayOf(174.61, 220.00, 261.63, 329.63), // Fmaj7
                    doubleArrayOf(196.00, 246.94, 293.66, 392.00)  // G6
                )
                AmbientTrack.LOFI_PIANO -> listOf(
                    doubleArrayOf(130.81, 164.81, 196.00, 246.94), // Low Cmaj7
                    doubleArrayOf(110.00, 130.81, 164.81, 196.00), // Low Am7
                    doubleArrayOf(87.31, 110.00, 130.81, 164.81)   // Low Fmaj7
                )
                AmbientTrack.DEEP_FOCUS -> listOf(
                    doubleArrayOf(65.41, 130.81, 196.00, 293.66),  // Deep C drone
                    doubleArrayOf(55.00, 110.00, 164.81, 246.94)   // Deep A drone
                )
                AmbientTrack.ACTION_CINEMATIC -> listOf(
                    doubleArrayOf(82.41, 123.47, 164.81, 246.94),  // E minor action
                    doubleArrayOf(73.42, 110.00, 146.83, 220.00),  // D minor action
                    doubleArrayOf(65.41, 98.00, 130.81, 196.00)    // C major action
                )
                else -> listOf(
                    doubleArrayOf(130.81, 261.63, 392.00)
                )
            }

            val chordDurationSamples = sampleRate * 3 // 3 seconds per chord progression

            while (isActive) {
                val currentTrackType = _currentTrack.value
                if (currentTrackType == AmbientTrack.OFF) break

                val targetGain = if (_isTtsActive.value) {
                    _volume.value * 0.28f // Lower background volume when voice speaks
                } else {
                    _volume.value * 1.0f  // Raise background music volume up loud when voice pauses!
                }

                val lpAlpha = _lowPassRatio.value

                for (i in pcmBuffer.indices) {
                    val time = sampleIndex / sampleRate.toDouble()
                    sampleIndex++

                    // Smooth exponential gain swell to avoid audio pops
                    currentGain += (targetGain - currentGain) * 0.008f

                    val chordIndex = ((sampleIndex / chordDurationSamples) % chords.size).toInt()
                    val activeChord = chords[chordIndex]

                    val rawSampleValue = when (currentTrackType) {
                        AmbientTrack.ALPHA_10HZ -> {
                            // Carrier 200 Hz, Beat frequency 10 Hz
                            val carrier = sin(2 * PI * 200.0 * time) * 0.25
                            val pulse = sin(2 * PI * 10.0 * time) * 0.5 + 0.5
                            carrier * pulse
                        }
                        AmbientTrack.THETA_6HZ -> {
                            // Carrier 150 Hz, Beat frequency 6 Hz
                            val carrier = sin(2 * PI * 150.0 * time) * 0.3
                            val pulse = sin(2 * PI * 6.0 * time) * 0.5 + 0.5
                            carrier * pulse
                        }
                        AmbientTrack.GAMMA_40HZ -> {
                            // Carrier 240 Hz, Gamma frequency 40 Hz modulation for focus
                            val carrier = sin(2 * PI * 240.0 * time) * 0.22
                            val pulse = sin(2 * PI * 40.0 * time) * 0.35 + 0.65
                            carrier * pulse
                        }
                        AmbientTrack.SOLFEGGIO_432HZ -> {
                            // Sacred 432 Hz Solfeggio with warm 216 Hz sub-octave and 864 Hz harmonic
                            val base432 = sin(2 * PI * 432.0 * time) * 0.2
                            val sub216 = sin(2 * PI * 216.0 * time) * 0.12
                            val octave864 = sin(2 * PI * 864.0 * time) * 0.05
                            base432 + sub216 + octave864
                        }
                        AmbientTrack.OCEAN_RAIN -> {
                            val waveMod = (sin(2 * PI * time * 0.15) * 0.4 + 0.5)
                            val noise = fastNoise() * waveMod * 0.15
                            val lowHum = sin(2 * PI * 110.0 * time) * 0.08
                            (noise + lowHum)
                        }
                        AmbientTrack.ACTION_CINEMATIC -> {
                            // Driving 130 BPM action percussion beat & synth bass pulse
                            val beatFrequency = 130.0 / 60.0 // 2.166 Hz beat cycle
                            val beatPhase = (time * beatFrequency) % 1.0
                            
                            // Kick / Sub-bass pulse on each beat
                            val kickEnvelope = (1.0 - beatPhase) * (1.0 - beatPhase)
                            val kickTone = sin(2 * PI * 65.0 * time) * kickEnvelope * 0.35

                            // Snare / Rimshot noise crack on offbeats
                            val subBeatPhase = (time * beatFrequency * 2.0) % 1.0
                            val snareEnvelope = if (subBeatPhase > 0.5) (1.0 - (subBeatPhase - 0.5) * 2.0).coerceAtLeast(0.0) else 0.0
                            val snareNoise = fastNoise() * snareEnvelope * 0.15

                            // Driving rhythmic arpeggiated bass chord
                            val arpNoteIndex = ((time * beatFrequency * 4.0) % activeChord.size).toInt()
                            val arpFreq = activeChord[arpNoteIndex]
                            val arpPulse = sin(2 * PI * arpFreq * time) * 0.18

                            (kickTone + snareNoise + arpPulse)
                        }
                        else -> {
                            var mix = 0.0
                            val chordPhase = (sampleIndex % chordDurationSamples) / chordDurationSamples.toDouble()
                            val envelope = sin(PI * chordPhase)
                            
                            for ((fIdx, freq) in activeChord.withIndex()) {
                                val harmonicWeight = 1.0 / (fIdx + 1)
                                mix += sin(2 * PI * freq * time) * harmonicWeight
                            }
                            mix * envelope * 0.2
                        }
                    }

                    // Single-pole IIR DSP Low-Pass Filter
                    val filteredSample = previousFilteredSample + lpAlpha * (rawSampleValue - previousFilteredSample)
                    previousFilteredSample = filteredSample

                    // Apply current ducked gain
                    val finalSample = filteredSample * currentGain

                    val pcm16 = (finalSample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                    pcmBuffer[i] = pcm16
                }

                val written = track.write(pcmBuffer, 0, pcmBuffer.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    TtsDiagnosticLogger.log(LogEventType.BUFFER_UNDERFLOW, "AudioTrack write error: $written", isError = true)
                }

                try {
                    val underrunCount = track.underrunCount
                    if (underrunCount > 0 && sampleIndex % 44100 == 0L) {
                        TtsDiagnosticLogger.log(
                            LogEventType.BUFFER_UNDERFLOW,
                            "AudioTrack PCM buffer underflow count: $underrunCount",
                            bufferUnderflowCount = underrunCount
                        )
                    }
                } catch (_: Exception) {}
            }

            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
