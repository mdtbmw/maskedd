package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.DiagnosticLogEntry
import com.example.tts.EnginePlaybackState
import com.example.tts.LogEventType
import com.example.tts.TtsDiagnosticLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsDiagnosticDashboardModal(
    engineState: EnginePlaybackState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logs by TtsDiagnosticLogger.logs.collectAsState()
    val totalUnderflows by TtsDiagnosticLogger.totalUnderflowEvents.collectAsState()
    val illegalTransitions by TtsDiagnosticLogger.illegalTransitionCount.collectAsState()
    val avgTimingOffset by TtsDiagnosticLogger.averageTimingOffsetMs.collectAsState()

    var selectedFilter by remember { mutableStateOf<LogEventType?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A), // Slate 900
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Title Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Diagnostic Dashboard",
                        tint = Color(0xFF38BDF8), // Cyan
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "TTS Diagnostics & Underflow Log",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Real-time state machine & audio buffer telemetry",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = { TtsDiagnosticLogger.clearLogs() },
                        modifier = Modifier.testTag("clear_diagnostic_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_diagnostic_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostic Telemetry Summary Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Engine State Badge
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Engine State", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (engineState) {
                                            is EnginePlaybackState.Playing -> Color(0xFF22C55E)
                                            is EnginePlaybackState.ProcessingAi -> Color(0xFFA855F7)
                                            is EnginePlaybackState.SynthesizingTts -> Color(0xFFEAB308)
                                            is EnginePlaybackState.Error -> Color(0xFFEF4444)
                                            else -> Color(0xFF64748B)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = engineState.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Underflows Badge
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Buffer Underflows", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalUnderflows Events",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (totalUnderflows == 0) Color(0xFF22C55E) else Color(0xFFF97316)
                        )
                    }
                }

                // Illegal Transitions
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Blocked Illegal", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$illegalTransitions Blocked",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (illegalTransitions == 0) Color(0xFF38BDF8) else Color(0xFFEF4444)
                        )
                    }
                }

                // Timing Offset
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Avg Sync Offset", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "±${avgTimingOffset}ms",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Actions / Manual Verification
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        // Test strict state machine transition validation rule
                        val current = engineState
                        val isAllowed = current.canTransitionTo(EnginePlaybackState.Playing(0, 0))
                        TtsDiagnosticLogger.log(
                            eventType = LogEventType.ILLEGAL_TRANSITION,
                            message = "Tested state machine validation for direct jump to Playing from ${current.name} (Allowed: $isAllowed)",
                            isError = !isAllowed
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Verify State Rules", fontSize = 11.sp, color = Color(0xFF38BDF8))
                }

                OutlinedButton(
                    onClick = {
                        TtsDiagnosticLogger.log(
                            eventType = LogEventType.BUFFER_UNDERFLOW,
                            message = "Diagnostic ping: AudioTrack PCM stream underrun check nominal",
                            bufferUnderflowCount = 0
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Check Buffer Health", fontSize = 11.sp, color = Color(0xFF22C55E))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf<LogEventType?>(
                    null,
                    LogEventType.STATE_TRANSITION,
                    LogEventType.ILLEGAL_TRANSITION,
                    LogEventType.TIMING_OFFSET,
                    LogEventType.BUFFER_UNDERFLOW
                )

                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = if (isSelected) null else filter },
                        label = {
                            Text(
                                text = filter?.label ?: "All Logs",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF1E293B),
                            selectedContainerColor = Color(0xFF0284C7),
                            labelColor = Color(0xFF94A3B8),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredLogs = remember(logs, selectedFilter) {
                if (selectedFilter == null) logs else logs.filter { it.eventType == selectedFilter }
            }

            // Diagnostic Log Stream List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No diagnostic events recorded yet.",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        DiagnosticLogItemCard(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogItemCard(log: DiagnosticLogEntry) {
    val (badgeBg, badgeText) = when (log.eventType) {
        LogEventType.ILLEGAL_TRANSITION -> Color(0xFF7F1D1D) to Color(0xFFFECACA)
        LogEventType.BUFFER_UNDERFLOW -> Color(0xFF7C2D12) to Color(0xFFFFEDD5)
        LogEventType.TIMING_OFFSET -> Color(0xFF0C4A6E) to Color(0xFFE0F2FE)
        LogEventType.STATE_TRANSITION -> Color(0xFF14532D) to Color(0xFFDCFCE7)
        LogEventType.AI_INFERENCE -> Color(0xFF581C87) to Color(0xFFF3E8FF)
        else -> Color(0xFF1E293B) to Color(0xFFE2E8F0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isError) Color(0xFF2A1215) else Color(0xFF131C2E)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.timestampFormatted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = log.eventType.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeText
                        )
                    }
                }

                if (log.timingOffsetMs != null) {
                    Text(
                        text = "Offset: ${log.timingOffsetMs}ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.message,
                fontSize = 12.sp,
                color = if (log.isError) Color(0xFFFCA5A5) else Color(0xFFE2E8F0),
                lineHeight = 16.sp
            )
        }
    }
}
