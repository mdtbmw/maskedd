package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.PlaybackProgress
import kotlin.math.roundToInt

/**
 * Module 2: The Cognitive Orb (Floating In-App & PIP Overlay Widget)
 * Features a circular Orb surrounded by a 3px dynamic progress stroke,
 * draggable touch positioning, single-tap play/pause controls, and
 * double-tap to maximize back to full reader view.
 */
@Composable
fun FloatingProgressShowerWidget(
    progress: PlaybackProgress,
    activeDocumentTitle: String,
    onTogglePlayPause: () -> Unit,
    onExpandReader: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isExpandedControls by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress.progressPercentage.coerceIn(0f, 1f),
        label = "floatingProgress"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF130E26).copy(alpha = 0.94f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Circular "Cognitive Orb" UI with 3px stroke progress ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                isExpandedControls = !isExpandedControls
                                onTogglePlayPause()
                            },
                            onDoubleTap = {
                                onExpandReader()
                            }
                        )
                    }
            ) {
                // 3px Progress Stroke Circle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidthPx = 3.dp.toPx()
                    val radius = (size.minDimension - strokeWidthPx) / 2f

                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = radius,
                        style = Stroke(width = strokeWidthPx)
                    )

                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF00F2FE), Color(0xFF8B5CF6))
                        ),
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                // Inner Glowing Orb Core
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFF1E1B4B))
                            )
                        )
                ) {
                    if (progress.isPlaying) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = Color(0xFF00F2FE),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Paused",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expandable Title & Controls Panel
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 160.dp)
                            .clickable { onExpandReader() }
                    ) {
                        Text(
                            text = activeDocumentTitle,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}% • Word ${progress.currentWordIndex}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFA78BFA),
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Single Tap Controls (Play/Pause)
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8B5CF6))
                    ) {
                        Icon(
                            imageVector = if (progress.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Double-tap/click to Maximize
                    IconButton(
                        onClick = onExpandReader,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Maximize",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Close Orb
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
