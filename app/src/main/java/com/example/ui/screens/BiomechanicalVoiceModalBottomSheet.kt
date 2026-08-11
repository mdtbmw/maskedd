package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
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
import com.example.tts.AcousticRoom
import com.example.tts.BiomechanicalVoiceEngine
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiomechanicalVoiceModalBottomSheet(
    engine: BiomechanicalVoiceEngine,
    onDismiss: () -> Unit
) {
    val profile by engine.profile.collectAsState()
    val fatigue by engine.vocalFatiguePercentage.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0D1117)
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
                                    listOf(Color(0xFF00F2FE), Color(0xFF4FACFE))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "MaskedD Voice Physics",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MaskedD Biomechanical Engine",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Zero-Data Throat Geometry & Ray-Traced Physics",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF00F2FE))
                        )
                    }
                }

                Button(
                    onClick = { engine.simulateSipOfWater() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Sip Water",
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hydrate Throat", color = Color(0xFF00F2FE), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Vocal Fatigue & Biological State Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BIOLOGICAL THROAT STATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Text(
                            text = "Fatigue: ${fatigue.roundToInt()}% | Hydration: ${(profile.hydrationLevel * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (profile.hydrationLevel < 0.4f) Color(0xFFEF4444) else Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { fatigue / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = Color(0xFF3B82F6),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Digital Vocal Cord Length Slider
            Text(
                text = "Digital Vocal Cord Length: ${String.format("%.1f", profile.vocalCordLengthMm)} mm",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Shorter = Brighter/Higher Pitch • Longer = Deep Male Resonance",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
            Slider(
                value = profile.vocalCordLengthMm,
                onValueChange = { engine.updateVocalCordLength(it) },
                valueRange = 10f..26f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00F2FE), activeTrackColor = Color(0xFF00F2FE))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Laryngeal Tension Slider
            Text(
                text = "Laryngeal Tension: ${(profile.laryngealTension * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Simulates physical constriction of vocal cords",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
            Slider(
                value = profile.laryngealTension,
                onValueChange = { engine.updateLaryngealTension(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Chest Cavity Resonant Volume Slider
            Text(
                text = "Chest Cavity Volume: ${(profile.chestCavityVolume * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Slider(
                value = profile.chestCavityVolume,
                onValueChange = { engine.updateChestCavityVolume(it) },
                valueRange = 0.5f..2.2f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF818CF8), activeTrackColor = Color(0xFF818CF8))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Adrenaline Heart-Rate Spike Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Adrenaline Heart-Rate Spike",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Forces digital lung pressure & sharp breath physics during action scenes",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }
                Switch(
                    checked = profile.adrenalineSpike > 0.5f,
                    onCheckedChange = { engine.setAdrenalineSpike(if (it) 1.0f else 0.0f) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFF43F5E))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Acoustic Ray-Tracing Room Dimensions
            Text(
                text = "Acoustic 3D Ray-Tracing Space",
                style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            AcousticRoom.values().forEach { room ->
                val isSelected = profile.acousticRoomDimensions == room
                Card(
                    onClick = { engine.setAcousticRoom(room) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF00F2FE) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = room.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Reverb: ${room.reverbDecayMs}ms | Damping: ${(room.wallDamping * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
