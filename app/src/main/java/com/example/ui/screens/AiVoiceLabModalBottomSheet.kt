package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiVoiceLabModalBottomSheet(
    onOpenVoiceCharacters: () -> Unit,
    onOpenPodcast: () -> Unit,
    onOpenSprint: () -> Unit,
    onOpenSocratic: () -> Unit,
    onOpenSentient: () -> Unit,
    onOpenBiomechanical: () -> Unit,
    onOpenAmbientMusic: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0D0A1B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("ai_voice_lab_modal_bottom_sheet")
        ) {
            // Top Header Title & Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF00F2FE))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Hub",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "AI Voice & Cognitive Hub",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "Real-time speech synthesis, podcast debate, RSVP sprint & audio physics",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFA78BFA),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Card 1: 2-Host AI Podcast Debate Generator
            LabToolCard(
                title = "2-Host AI Podcast Generator",
                subtitle = "Synthesizes chapter content into a lively, conversational debate between Host A & Host B.",
                icon = Icons.Default.Podcasts,
                accentColor = Color(0xFF8B5CF6),
                badgeText = "Podcasts",
                actionButtonText = "Generate Podcast",
                onClick = {
                    onDismiss()
                    onOpenPodcast()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 2: Kinetic Speed Sprint Reader (RSVP)
            LabToolCard(
                title = "Kinetic RSVP Speed Sprint",
                subtitle = "Rapid serial visual presentation up to 1000 WPM with focal letter highlighting & speed controls.",
                icon = Icons.Default.Speed,
                accentColor = Color(0xFFF59E0B),
                badgeText = "RSVP 1000 WPM",
                actionButtonText = "Launch Sprint",
                onClick = {
                    onDismiss()
                    onOpenSprint()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 3: Human AI Characters & Voice Cloning
            LabToolCard(
                title = "AI Voice Characters & Cloning",
                subtitle = "15+ natural human storytellers (Nigerian Kofi/Amina, Kid Leo, British Arthur) + Instant Voice Cloning.",
                icon = Icons.Default.Air,
                accentColor = Color(0xFFEC4899),
                badgeText = "15+ Voices",
                actionButtonText = "Select Voice",
                onClick = {
                    onDismiss()
                    onOpenVoiceCharacters()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 4: Sentient Articulation SLM Engine
            LabToolCard(
                title = "Sentient Articulation SLM",
                subtitle = "5-sentence lookahead neural prosody, emotional mood parsing & diaphragm breath pacing.",
                icon = Icons.Default.Psychology,
                accentColor = Color(0xFF10B981),
                badgeText = "Prosody SLM",
                actionButtonText = "View Prosody",
                onClick = {
                    onDismiss()
                    onOpenSentient()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 5: Biomechanical Throat Physics
            LabToolCard(
                title = "Biomechanical Throat Physics",
                subtitle = "Laryngeal resonance, vocal cord hydration sips, saliva friction & acoustic filter modulation.",
                icon = Icons.Default.GraphicEq,
                accentColor = Color(0xFF00F2FE),
                badgeText = "Physics 100%",
                actionButtonText = "Tune Physics",
                onClick = {
                    onDismiss()
                    onOpenBiomechanical()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 6: 3D Generative Foley & Ambient Soundscapes
            LabToolCard(
                title = "Generative Foley & Soundscapes",
                subtitle = "Lo-fi study beats, rainy window, forest cabin & real-time auto-ducking audio music mixer.",
                icon = Icons.Default.MusicNote,
                accentColor = Color(0xFF3B82F6),
                badgeText = "Atmosphere 3D",
                actionButtonText = "Open Mixer",
                onClick = {
                    onDismiss()
                    onOpenAmbientMusic()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 7: Socratic AI Co-Pilot & Chapter Recaps
            LabToolCard(
                title = "Socratic AI Co-Pilot & Recaps",
                subtitle = "Conversational co-reader, 'Wait, Who?' character recaps & instant comprehension quizzes.",
                icon = Icons.Default.AutoAwesome,
                accentColor = Color(0xFFA855F7),
                badgeText = "Socratic AI",
                actionButtonText = "Ask Co-Pilot",
                onClick = {
                    onDismiss()
                    onOpenSocratic()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Card 8: Engine Health & Diagnostics
            LabToolCard(
                title = "TTS Engine Health & Diagnostics",
                subtitle = "Real-time stream buffer monitor, API latency tracking & automatic credit-fallback logger.",
                icon = Icons.Default.BugReport,
                accentColor = Color(0xFF38BDF8),
                badgeText = "Diagnostics",
                actionButtonText = "Health Logs",
                onClick = {
                    onDismiss()
                    onOpenDiagnostics()
                }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LabToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String,
    actionButtonText: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.22f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Button Row inside Card for explicit clarity and instant affordance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.25f))
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { onClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚡ $actionButtonText",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
