package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.InstantVoiceCloneUploader
import com.example.tts.VoiceCharacter
import com.example.tts.VoiceRecorder
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantVoiceCloneModal(
    onVoiceCloned: (VoiceCharacter) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    val uploader = remember { InstantVoiceCloneUploader(context) }

    val isRecording by recorder.isRecording.collectAsState()
    val recordingSeconds by recorder.recordingSeconds.collectAsState()

    var voiceName by remember { mutableStateOf("") }
    var voiceDescription by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎙️") }
    var recordedFile by remember { mutableStateOf<File?>(null) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successCharacter by remember { mutableStateOf<VoiceCharacter?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    ModalBottomSheet(
        onDismissRequest = {
            if (isRecording) recorder.stopRecording()
            onDismiss()
        },
        containerColor = Color(0xFF130E22),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .testTag("instant_voice_clone_modal"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎙️ Instant Voice Cloning (30s Sample)",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                )
            )

            Text(
                text = "Record 30 seconds of clear speech to synthesize a custom voice profile",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            if (successCharacter != null) {
                // Success State View
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Voice Profile '${successCharacter!!.name}' Created!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mapped to Room database & ready for playback.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            onVoiceCloned(successCharacter!!)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Reading with ${successCharacter!!.name}")
                    }
                }
            } else {
                // Recording & Metadata View
                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    label = { Text("Voice Profile Name", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("e.g. My Narrator Voice", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mic Recording Circle Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .padding(vertical = 12.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFFEF4444).copy(alpha = 0.8f), Color.Transparent)
                                    )
                                )
                        )
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current
                    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            recordedFile = null
                            recorder.startRecording(coroutineScope)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) Color(0xFFEF4444)
                                else if (recordedFile != null) Color(0xFF10B981)
                                else Color(0xFF8B5CF6)
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (isRecording) {
                                    recordedFile = recorder.stopRecording()
                                } else {
                                    val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        recordedFile = null
                                        recorder.startRecording(coroutineScope)
                                    } else {
                                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().testTag("record_mic_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop" else "Record",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Text(
                    text = if (isRecording) "Recording Voice Sample: $recordingSeconds / 30s"
                    else if (recordedFile != null) "✅ 30s Recording Captured!"
                    else "Tap microphone to record sample",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color(0xFFEF4444) else if (recordedFile != null) Color(0xFF10B981) else Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFEF4444)),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        val fileToUpload = recordedFile
                        if (fileToUpload == null || !fileToUpload.exists()) {
                            errorMessage = "Please record a voice sample first."
                            return@Button
                        }
                        isUploading = true
                        uploadStatusMessage = "Synthesizing voice profile..."
                        errorMessage = null

                        coroutineScope.launch {
                            try {
                                val result = uploader.uploadVoiceClone(
                                    audioFile = fileToUpload,
                                    voiceName = voiceName.ifBlank { "My Voice Clone" },
                                    description = voiceDescription.ifBlank { "User custom cloned AI voice profile" },
                                    emoji = selectedEmoji
                                )
                                successCharacter = result
                            } catch (e: Exception) {
                                errorMessage = "Upload failed: ${e.message}"
                            } finally {
                                isUploading = false
                            }
                        }
                    },
                    enabled = recordedFile != null && !isUploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("upload_clone_button")
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(uploadStatusMessage)
                    } else {
                        Icon(imageVector = Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create & Map Custom Voice Profile")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
