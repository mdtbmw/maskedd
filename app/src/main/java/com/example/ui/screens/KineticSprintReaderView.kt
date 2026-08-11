package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.ParsedDocument
import com.example.tts.PlaybackProgress
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun KineticSprintReaderView(
    parsedDoc: ParsedDocument?,
    playbackProgress: PlaybackProgress,
    onWordSeek: (Int) -> Unit
) {
    if (parsedDoc == null || parsedDoc.words.isEmpty()) return

    var targetWpm by remember { mutableStateOf(350) } // Words Per Minute
    var isRunning by remember { mutableStateOf(false) }

    // Correctly bind to currentWordIndex (NOT sentence index)
    var currentWordIndex by remember {
        mutableStateOf(playbackProgress.currentWordIndex.coerceIn(0, (parsedDoc.words.size - 1).coerceAtLeast(0)))
    }

    // Keep synced with global TTS playback position when not actively sprinting locally
    LaunchedEffect(playbackProgress.currentWordIndex) {
        if (!isRunning) {
            currentWordIndex = playbackProgress.currentWordIndex.coerceIn(0, (parsedDoc.words.size - 1).coerceAtLeast(0))
        }
    }

    // High speed RSVP Sprint Loop
    LaunchedEffect(isRunning, targetWpm) {
        if (isRunning) {
            val msPerWord = (60_000f / targetWpm).roundToInt().toLong().coerceAtLeast(50L)
            while (isRunning && currentWordIndex < parsedDoc.words.size - 1) {
                delay(msPerWord)
                currentWordIndex++
                onWordSeek(currentWordIndex)
            }
            if (currentWordIndex >= parsedDoc.words.size - 1) {
                isRunning = false
            }
        }
    }

    val wordObj = parsedDoc.words.getOrNull(currentWordIndex)
    val wordStr = wordObj?.word ?: "Sprint"

    // Previous and Next words for contextual awareness
    val prevWordStr = parsedDoc.words.getOrNull(currentWordIndex - 1)?.word ?: ""
    val nextWordStr = parsedDoc.words.getOrNull(currentWordIndex + 1)?.word ?: ""

    // Focal Fixation calculations for RSVP (Rapid Serial Visual Presentation)
    val fixationIndex = (wordStr.length * 0.35f).roundToInt().coerceIn(0, (wordStr.length - 1).coerceAtLeast(0))
    val prefix = wordStr.take(fixationIndex)
    val focalChar = wordStr.getOrNull(fixationIndex)?.toString() ?: ""
    val suffix = wordStr.drop(fixationIndex + 1)

    val scaleAnimate by animateFloatAsState(
        targetValue = if (isRunning) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "sprintScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚡ KINETIC SPRINT ENGINE (RSVP)",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFFF59E0B),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // RSVP Central Focal Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13111C)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .scale(scaleAnimate)
                .border(2.dp, Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899))), RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .background(Color(0xFFF59E0B))
                    )

                    // Context word preview above
                    Text(
                        text = prevWordStr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.35f),
                            fontWeight = FontWeight.Normal
                        )
                    )

                    val annotatedWord = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)) {
                            append(prefix)
                        }
                        withStyle(SpanStyle(color = Color(0xFFF59E0B), fontWeight = FontWeight.Black, fontSize = 42.sp)) {
                            append(focalChar)
                        }
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)) {
                            append(suffix)
                        }
                    }

                    Text(
                        text = annotatedWord,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Context word preview below
                    Text(
                        text = nextWordStr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.35f),
                            fontWeight = FontWeight.Normal
                        )
                    )

                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .background(Color(0xFFF59E0B))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Word Position Slider
        Slider(
            value = currentWordIndex.toFloat(),
            onValueChange = { newIdx ->
                currentWordIndex = newIdx.roundToInt().coerceIn(0, parsedDoc.words.size - 1)
                onWordSeek(currentWordIndex)
            },
            valueRange = 0f..(parsedDoc.words.size - 1).coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFEC4899),
                activeTrackColor = Color(0xFFF59E0B)
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Progress Counter
        Text(
            text = "Word ${currentWordIndex + 1} of ${parsedDoc.words.size} (${((currentWordIndex.toFloat() / parsedDoc.words.size.coerceAtLeast(1)) * 100).toInt()}%)",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // WPM Speed Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Speed, contentDescription = "WPM", tint = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$targetWpm WPM",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
        }

        Slider(
            value = targetWpm.toFloat(),
            onValueChange = { targetWpm = it.roundToInt() },
            valueRange = 150f..800f,
            steps = 13,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B), activeTrackColor = Color(0xFFF59E0B)),
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    currentWordIndex = (currentWordIndex - 50).coerceAtLeast(0)
                    onWordSeek(currentWordIndex)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(imageVector = Icons.Default.Replay, contentDescription = "Rewind 50 Words", tint = Color.White)
            }

            FloatingActionButton(
                onClick = { isRunning = !isRunning },
                containerColor = Color(0xFFF59E0B),
                contentColor = Color.Black,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Start Sprint",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
