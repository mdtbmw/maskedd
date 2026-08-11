package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.PI

/**
 * High-performance canvas-based waveform visualizer.
 * Displays real-time audio playback progress with dynamic animated amplitude bars,
 * scrubbing gesture detection, and vibrant theme gradients.
 */
@Composable
fun CanvasWaveformVisualizer(
    progressPercentage: Float,
    isPlaying: Boolean,
    audioAmplitudes: List<Float> = emptyList(),
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.White.copy(alpha = 0.20f),
    barCount: Int = 42,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Base waveform shape seed
    val baseWaveHeights = remember(barCount) {
        List(barCount) { i ->
            val norm = i.toFloat() / barCount
            val sin1 = sin(norm * PI * 3).toFloat()
            val sin2 = sin(norm * PI * 7).toFloat()
            (0.20f + (sin1 * 0.45f).coerceAtLeast(0f) + (sin2 * 0.25f).coerceAtLeast(0f)).coerceIn(0.15f, 0.95f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("canvas_waveform_visualizer")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val spacing = 4.dp.toPx()
            val totalSpacing = spacing * (barCount - 1)
            val barWidth = ((width - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())

            val progressX = width * progressPercentage.coerceIn(0f, 1f)

            for (i in 0 until barCount) {
                val x = i * (barWidth + spacing)
                val baseHeightFraction = baseWaveHeights[i]

                // Dynamic live audio buffer amplitude modulation
                val liveAmp = if (audioAmplitudes.isNotEmpty()) {
                    val ampIdx = (i * audioAmplitudes.size / barCount).coerceIn(0, audioAmplitudes.lastIndex)
                    audioAmplitudes[ampIdx]
                } else 0f

                val pulseFactor = if (isPlaying) {
                    val phaseOffset = i * 0.25f
                    sin(wavePhase + phaseOffset) * 0.22f + (liveAmp * 0.40f)
                } else 0f

                val heightFraction = (baseHeightFraction + pulseFactor).coerceIn(0.12f, 1.0f)
                val barHeight = height * heightFraction
                val topY = (height - barHeight) / 2f

                val isPassed = x <= progressX

                val barColor = if (isPassed) {
                    activeColor
                } else {
                    inactiveColor
                }

                // Draw rounded vertical bar
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }

            // Scrubbing playhead cursor line
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White, activeColor)
                ),
                topLeft = Offset(progressX - 1.5.dp.toPx(), 0f),
                size = Size(3.dp.toPx(), height),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}
