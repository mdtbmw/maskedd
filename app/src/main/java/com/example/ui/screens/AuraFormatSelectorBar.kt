package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.DynamicFormatMode

@Composable
fun AuraFormatSelectorBar(
    currentMode: DynamicFormatMode,
    onModeSelected: (DynamicFormatMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DynamicFormatMode.values().forEach { mode ->
            val isSelected = currentMode == mode
            val modeColor = when (mode) {
                DynamicFormatMode.DEEP_DIVE -> Color(0xFF10B981)
                DynamicFormatMode.PODCAST_DEBATE -> Color(0xFF8B5CF6)
                DynamicFormatMode.KINETIC_SPRINT -> Color(0xFFF59E0B)
                DynamicFormatMode.DIALOGUE_ONLY -> Color(0xFFEC4899)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) modeColor.copy(alpha = 0.25f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) modeColor else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (mode) {
                            DynamicFormatMode.DEEP_DIVE -> Icons.Default.AutoAwesome
                            DynamicFormatMode.PODCAST_DEBATE -> Icons.Default.Podcasts
                            DynamicFormatMode.KINETIC_SPRINT -> Icons.Default.Speed
                            DynamicFormatMode.DIALOGUE_ONLY -> Icons.Default.TheaterComedy
                        },
                        contentDescription = mode.displayName,
                        tint = if (isSelected) modeColor else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (mode) {
                            DynamicFormatMode.DEEP_DIVE -> "Deep"
                            DynamicFormatMode.PODCAST_DEBATE -> "Podcast"
                            DynamicFormatMode.KINETIC_SPRINT -> "Sprint"
                            DynamicFormatMode.DIALOGUE_ONLY -> "Script"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) Color.White else Color.Gray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
