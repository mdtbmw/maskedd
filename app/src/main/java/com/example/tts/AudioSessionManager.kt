package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe AudioSessionManager.
 * Manages unique Android AudioSession IDs, recycles MediaPlayers cleanly,
 * and eliminates multi-voice artifacts across rapid sentence switches.
 */
object AudioSessionManager {

    private val sessionCounter = AtomicInteger(2001)
    private val activeMediaPlayers = ConcurrentHashMap.newKeySet<MediaPlayer>()

    /**
     * Generates a guaranteed unique audio session ID for system media routing.
     */
    @Synchronized
    fun generateUniqueAudioSessionId(context: Context): Int {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val sysSessionId = audioManager?.generateAudioSessionId()
            if (sysSessionId != null && sysSessionId != AudioManager.ERROR) {
                sysSessionId
            } else {
                sessionCounter.incrementAndGet()
            }
        } catch (_: Exception) {
            sessionCounter.incrementAndGet()
        }
    }

    /**
     * Creates a new managed MediaPlayer instance assigned to a unique audio session ID.
     * Automatically stops and recycles any currently active media players to prevent multi-voice artifacts.
     */
    @Synchronized
    fun createManagedMediaPlayer(audioSessionId: Int): MediaPlayer {
        releaseAllActiveMediaPlayers()

        val mediaPlayer = MediaPlayer().apply {
            setAudioSessionId(audioSessionId)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        }
        activeMediaPlayers.add(mediaPlayer)
        return mediaPlayer
    }

    /**
     * Safely recycles and releases a specific MediaPlayer instance.
     */
    @Synchronized
    fun releaseMediaPlayer(mediaPlayer: MediaPlayer?) {
        if (mediaPlayer == null) return
        try {
            activeMediaPlayers.remove(mediaPlayer)
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()
            mediaPlayer.release()
        } catch (_: Exception) {}
    }

    /**
     * Recycles and cleans up all active MediaPlayers in memory.
     */
    @Synchronized
    fun releaseAllActiveMediaPlayers() {
        val iterator = activeMediaPlayers.iterator()
        while (iterator.hasNext()) {
            val mp = iterator.next()
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            } catch (_: Exception) {}
            iterator.remove()
        }
    }
}
