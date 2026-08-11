package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.HumanRespiratoryEvent
import com.example.tts.RespiratorySynthesisEngine
import com.example.tts.VoiceCharacterManager

@Composable
fun RespiratoryBreathingSphere(
    isPlaying: Boolean,
    audioAmplitudes: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    val respiratoryState by RespiratorySynthesisEngine.respiratoryState.collectAsState()
    val activeCharacter by VoiceCharacterManager.selectedCharacter.collectAsState()

    // Compute live audio buffer intensity (0.0 to 1.0)
    val currentIntensity = if (isPlaying && audioAmplitudes.isNotEmpty()) {
        audioAmplitudes.average().toFloat().coerceIn(0f, 1f)
    } else 0f

    // Smoothly animate scale & alpha based on audio buffer intensity
    val animatedBufferScale by animateFloatAsState(
        targetValue = if (isPlaying) 0.95f + (currentIntensity * 0.25f) else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buffer_scale"
    )

    val animatedBufferAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.40f + (currentIntensity * 0.55f) else 0.25f,
        animationSpec = tween(150, easing = LinearEasing),
        label = "buffer_alpha"
    )

    // Breathing pulse fallback infinite transition
    val infiniteTransition = rememberInfiniteTransition(label = "respiratory_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (respiratoryState) {
                    is HumanRespiratoryEvent.Inhaling -> 1400
                    is HumanRespiratoryEvent.Chuckling -> 350
                    is HumanRespiratoryEvent.Exhaling -> 1800
                    else -> 1200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val dynamicScale = if (isPlaying && audioAmplitudes.isNotEmpty()) animatedBufferScale else pulseScale
    val dynamicAlpha = if (isPlaying && audioAmplitudes.isNotEmpty()) animatedBufferAlpha else 0.65f

    val auraColors = when (respiratoryState) {
        is HumanRespiratoryEvent.Chuckling -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))
        is HumanRespiratoryEvent.Inhaling -> listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF8B5CF6))
        is HumanRespiratoryEvent.Exhaling -> listOf(Color(0xFFA78BFA), Color(0xFF8B5CF6), Color(0xFF3B82F6))
        is HumanRespiratoryEvent.PausingToCheckUp -> listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFD97706))
        else -> listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF3B82F6))
    }

    val stateBadgeText = when (respiratoryState) {
        is HumanRespiratoryEvent.Inhaling -> "🫁 Natural Inhale..."
        is HumanRespiratoryEvent.Chuckling -> "😄 Chuckling & Expressive..."
        is HumanRespiratoryEvent.Exhaling -> "💨 Soft Exhale..."
        is HumanRespiratoryEvent.PausingToCheckUp -> "⏸️ Human Pause & Check-in..."
        else -> if (isPlaying) "🎙️ ${activeCharacter.name} Neural Speech" else "⏸️ Standby"
    }

    Column(
        modifier = modifier.testTag("respiratory_breathing_sphere"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(76.dp)
        ) {
            // Background Glow Aura bound to audio buffer intensity alpha & scale
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .scale(dynamicScale)
                    .clip(CircleShape)
                    .blur(16.dp)
                    .background(
                        Brush.radialGradient(
                            colors = auraColors.map { it.copy(alpha = dynamicAlpha) }
                        )
                    )
            )

            // Inner Character Avatar Orb bound to scale
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(dynamicScale)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(auraColors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeCharacter.avatarEmoji,
                    fontSize = 28.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Dynamic State Badge
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = stateBadgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            )
        }
    }
}
