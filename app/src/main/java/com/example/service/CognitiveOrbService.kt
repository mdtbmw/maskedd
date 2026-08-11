package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.tts.PlaybackProgress
import com.example.tts.SpeechEngine

/**
 * Module 2: The Cognitive Orb (Floating System Overlay Widget)
 * Uses SYSTEM_ALERT_WINDOW to draw an interactive circular "Orb" widget
 * over any Android screen with dynamic 3px progress ring, single tap play/pause,
 * and double tap to maximize back to MaskedD full app reader.
 */
class CognitiveOrbService : Service() {

    companion object {
        const val CHANNEL_ID = "masked_d_orb_channel"
        const val NOTIFICATION_ID = 2002
        var speechEngine: SpeechEngine? = null

        fun startOrb(context: Context) {
            val intent = Intent(context, CognitiveOrbService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopOrb(context: Context) {
            val intent = Intent(context, CognitiveOrbService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cognitive Orb Active")
            .setContentText("Floating picture-in-picture widget running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Setup WindowManager Floating Overlay View
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
            setupFloatingOrbView()
        }
    }

    private fun setupFloatingOrbView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val composeView = ComposeView(this).apply {
            setContent {
                val engine = speechEngine
                val progressState = engine?.progressState?.collectAsState()?.value ?: PlaybackProgress()

                CognitiveOrbUi(
                    progress = progressState,
                    onTogglePlayPause = {
                        if (progressState.isPlaying) engine?.pause() else engine?.play()
                    },
                    onMaximizeApp = {
                        val openIntent = Intent(this@CognitiveOrbService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(openIntent)
                    }
                )
            }
        }

        // Draggable Touch Listener for WindowManager
        composeView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false // Allow Compose tap detection
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(composeView, params)
                        return true
                    }
                }
                return false
            }
        })

        floatingView = composeView
        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MaskedD Cognitive Orb Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        floatingView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }
}

/**
 * The Cognitive Orb Composable:
 * Circular 72dp widget with 3px stroke dynamic progress ring, single-tap play/pause,
 * and double-tap to maximize full app.
 */
@Composable
fun CognitiveOrbUi(
    progress: PlaybackProgress,
    onTogglePlayPause: () -> Unit,
    onMaximizeApp: () -> Unit
) {
    var showControls by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progressPercentage.coerceIn(0f, 1f),
        label = "orbProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        onTogglePlayPause()
                    },
                    onDoubleTap = {
                        onMaximizeApp()
                    }
                )
            }
    ) {
        // 3px Stroke Edge Dynamic Progress Circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 3.dp.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2f

            // Track background
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = radius,
                style = Stroke(width = strokeWidthPx)
            )

            // Dynamic progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF00F2FE), Color(0xFF8B5CF6))
                ),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }

        // Inner Glowing Orb Circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6),
                            Color(0xFF1E1B4B)
                        )
                    )
                )
        ) {
            if (progress.isPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = Color(0xFF00F2FE),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Percentage Text
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 10.sp
                ),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
