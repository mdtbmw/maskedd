package com.example.tts

import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL
import platform.darwin.NSObject

actual class PlatformAudioPlayer {
    private var avPlayer: AVAudioPlayer? = null

    actual fun playFile(filePath: String, listener: PlatformAudioListener) {
        stop()
        try {
            val url = NSURL.fileURLWithPath(filePath)
            avPlayer = AVAudioPlayer(contentsOfURL = url, error = null)
            if (avPlayer != null) {
                avPlayer?.prepareToPlay()
                avPlayer?.play()
                listener.onAudioStart()
            } else {
                listener.onError("iOS AVAudioPlayer failed to initialize")
            }
        } catch (e: Exception) {
            listener.onError("iOS AVAudioPlayer exception: ${e.message}")
        }
    }

    actual fun pause() {
        try { avPlayer?.pause() } catch (_: Exception) {}
    }

    actual fun resume() {
        try { avPlayer?.play() } catch (_: Exception) {}
    }

    actual fun stop() {
        try {
            avPlayer?.stop()
            avPlayer = null
        } catch (_: Exception) {}
    }

    actual fun isPlaying(): Boolean {
        return avPlayer?.isPlaying() ?: false
    }

    actual fun setVolume(volume: Float) {
        try {
            avPlayer?.setVolume(volume.coerceIn(0.0f, 1.0f))
        } catch (_: Exception) {}
    }
}
