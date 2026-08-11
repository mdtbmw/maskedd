package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.AcousticBlueprint
import com.example.tts.SentientArticulationEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentientArticulationModalBottomSheet(
    engine: SentientArticulationEngine,
    currentSentenceIndex: Int,
    onDismiss: () -> Unit
) {
    val activeBlueprint by engine.activeSentenceBlueprint.collectAsState()
    val allBlueprints by engine.blueprints.collectAsState()
    val isAnalyzing by engine.isAnalyzingLookahead.collectAsState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F0C1B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Sentient Articulation Engine",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sentient Articulation Engine",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "5-Sentence Lookahead SLM & Diaphragm Protocol",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFA78BFA))
                        )
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            engine.playDiaphragmBreathSound()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = "Test Breath",
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Breath", color = Color(0xFFA78BFA), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Active Sentence Semantic Blueprint Card
            val blueprint = activeBlueprint ?: AcousticBlueprint(sentenceIndex = currentSentenceIndex)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT SENTENCE BLUEPRINT #${currentSentenceIndex + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Text(
                            text = blueprint.intent.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFEC4899),
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Velocity Curve", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                            Text(
                                "${String.format("%.2f", blueprint.velocityMultiplier)}x",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }

                        Column {
                            Text("Vocal Pitch", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                            Text(
                                "${String.format("%.2f", blueprint.pitchMultiplier)}x",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }

                        Column {
                            Text("Comma Breaths", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                            Text(
                                "${blueprint.commaBreathIndices.size} Inhalations",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            )
                        }

                        Column {
                            Text("Hesitations", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                            Text(
                                "${blueprint.microHesitationIndices.size} Soft Pauses",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Diaphragm Physics: " + when {
                            blueprint.isQuestionInflection -> "Cricothyroid Muscle Terminal Up-Pitch Stretch (?)"
                            blueprint.isExclamationPressure -> "Sudden High Lung Pressure Explosion (!)"
                            else -> "Smooth Trailing Deflation (.)"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5-Sentence Lookahead SLM Pipeline Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "5-Sentence Lookahead SLM Pipeline",
                    style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )

                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF8B5CF6),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val lookaheadItems = (currentSentenceIndex until currentSentenceIndex + 5).mapNotNull { allBlueprints[it] }

            if (lookaheadItems.isEmpty()) {
                Text(
                    text = "Lookahead pipeline initializing...",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            } else {
                lookaheadItems.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.sentenceIndex == currentSentenceIndex) Color(0xFF8B5CF6).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Sentence #${item.sentenceIndex + 1}: ${item.intent.displayName}",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = item.summaryInsight,
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                )
                            }

                            Text(
                                text = "${String.format("%.2f", item.velocityMultiplier)}x",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
