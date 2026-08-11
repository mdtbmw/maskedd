package com.example.tts

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

actual class PlatformAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    actual fun playFile(filePath: String, listener: PlatformAudioListener) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(filePath))
                setOnPreparedListener {
                    start()
                    listener.onAudioStart()
                }
                setOnCompletionListener {
                    listener.onAudioCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    listener.onError("Android MediaPlayer playback error")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            listener.onError("Android MediaPlayer exception: ${e.message}")
        }
    }

    actual fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    actual fun resume() {
        try {
            mediaPlayer?.start()
        } catch (_: Exception) {}
    }

    actual fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    actual fun isPlaying(): Boolean {
        return try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
    }

    actual fun setVolume(volume: Float) {
        try {
            val vol = volume.coerceIn(0.0f, 1.0f)
            mediaPlayer?.setVolume(vol, vol)
        } catch (_: Exception) {}
    }
}
