package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.tts.SpeechEngine

class TtsPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "masked_d_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_JUMP_BACK = "com.example.action.JUMP_BACK"
        const val ACTION_JUMP_FORWARD = "com.example.action.JUMP_FORWARD"

        var speechEngine: SpeechEngine? = null
        var activeDocumentTitle: String = "MaskedD Document Reader"
        var activeAuthor: String = "MaskedD AI Engine"
    }

    private val binder = LocalBinder()
    private var mediaSession: MediaSessionCompat? = null

    inner class LocalBinder : Binder() {
        fun getService(): TtsPlaybackService = this@TtsPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize Native MediaSession for Lockscreen Controls
        mediaSession = MediaSessionCompat(this, "MaskedDMediaSession").apply {
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    speechEngine?.play()
                    updateNotification()
                }

                override fun onPause() {
                    speechEngine?.pause()
                    updateNotification()
                }

                override fun onSkipToNext() {
                    speechEngine?.jumpForward(15)
                    updateNotification()
                }

                override fun onSkipToPrevious() {
                    speechEngine?.jumpBackward(15)
                    updateNotification()
                }

                override fun onStop() {
                    speechEngine?.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    val total = speechEngine?.progressState?.value?.totalWords ?: 1
                    val targetWord = ((pos.toFloat() / 1000f) * total).toInt()
                    speechEngine?.seekToWord(targetWord)
                    updateNotification()
                }
            })

            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> speechEngine?.play()
            ACTION_PAUSE -> speechEngine?.pause()
            ACTION_STOP -> {
                speechEngine?.stop()
                mediaSession?.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_JUMP_BACK -> speechEngine?.jumpBackward(10)
            ACTION_JUMP_FORWARD -> speechEngine?.jumpForward(10)
        }

        updateNotification()
        return START_STICKY
    }

    fun updateNotification() {
        val engine = speechEngine
        val state = engine?.progressState?.value ?: com.example.tts.PlaybackProgress()

        // 1. Sync MediaSession Metadata & Playback State for Lockscreen
        val playbackStateCode = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val currentPositionMs = (state.progressPercentage * 1000).toLong()

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(playbackStateCode, currentPositionMs, if (state.isPlaying) 1.0f else 0.0f)
            .build()

        mediaSession?.setPlaybackState(playbackState)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeDocumentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAuthor)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "MaskedD Sentient Reader")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 1000L)
            .build()

        mediaSession?.setMetadata(metadata)

        // 2. Build MediaStyle Notification
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (state.isPlaying) {
            val pauseIntent = Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_PAUSE }
            val pendingPause = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause", pendingPause
            )
        } else {
            val playIntent = Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_PLAY }
            val pendingPlay = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play", pendingPlay
            )
        }

        val jumpBackIntent = Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_JUMP_BACK }
        val pendingJumpBack = PendingIntent.getService(this, 3, jumpBackIntent, PendingIntent.FLAG_IMMUTABLE)

        val jumpForwardIntent = Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_JUMP_FORWARD }
        val pendingJumpForward = PendingIntent.getService(this, 4, jumpForwardIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 5, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val progressPercentInt = (state.progressPercentage * 100).toInt()
        val currentText = if (state.currentWord.isNotBlank()) {
            "Reading: \"${state.currentWord}\" • $progressPercentInt%"
        } else {
            "Word ${state.currentWordIndex} of ${state.totalWords} • $progressPercentInt%"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(activeDocumentTitle)
            .setContentText(currentText)
            .setSubText("MaskedD Expressive Engine")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenApp)
            .setOngoing(state.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(100, progressPercentInt, false)
            .addAction(android.R.drawable.ic_media_rew, "-10s", pendingJumpBack)
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_ff, "+10s", pendingJumpForward)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MaskedD Media Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lockscreen and Notification controls for background speech reading"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }
}
