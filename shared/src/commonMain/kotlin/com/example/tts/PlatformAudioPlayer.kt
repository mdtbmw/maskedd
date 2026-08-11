package com.example.tts

import kotlinx.coroutines.flow.StateFlow

interface PlatformAudioListener {
    fun onAudioStart()
    fun onAudioCompleted()
    fun onError(message: String)
}

/**
 * Kotlin Multiplatform Expect Audio Player Interface
 * Abstract layer allowing 100% shared Compose UI & ViewModels to play speech audio
 * natively on both Android (MediaPlayer) and iOS (AVAudioPlayer / AVFoundation).
 */
expect class PlatformAudioPlayer {
    fun playFile(filePath: String, listener: PlatformAudioListener)
    fun pause()
    fun resume()
    fun stop()
    fun isPlaying(): Boolean
    fun setVolume(volume: Float)
}
