package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.CloudVoiceSynthesizer
import com.example.tts.VoiceCharacter
import com.example.tts.VoiceCharacterManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCharactersModalBottomSheet(
    cloudSynthesizer: CloudVoiceSynthesizer?,
    onDismiss: () -> Unit
) {
    val allCharacters by VoiceCharacterManager.characters.collectAsState()
    val activeCharacter by VoiceCharacterManager.selectedCharacter.collectAsState()

    var selectedFilterTag by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showCloneModal by remember { mutableStateOf(false) }

    val filterCategories = listOf("All", "⭐ Favorites", "🇳🇬 Nigerian / African", "🧒 Kid & Youthful", "🎙️ Classic Storytellers", "📚 Academic & Bard", "🧘 Lo-Fi & Sci-Fi", "🎙️ Custom Cloned")

    val filteredCharacters = remember(allCharacters, selectedFilterTag, searchQuery) {
        allCharacters.filter { char ->
            val matchesSearch = searchQuery.isBlank() ||
                    char.name.contains(searchQuery, ignoreCase = true) ||
                    char.tag.contains(searchQuery, ignoreCase = true) ||
                    char.description.contains(searchQuery, ignoreCase = true)

            val matchesTag = when (selectedFilterTag) {
                "⭐ Favorites" -> char.isFavorite
                "🇳🇬 Nigerian / African" -> char.accentRegion.contains("Nigeria", ignoreCase = true) || char.tag.contains("Nigerian", ignoreCase = true) || char.tag.contains("African", ignoreCase = true)
                "🧒 Kid & Youthful" -> char.tag.contains("Kid", ignoreCase = true) || char.tag.contains("Youthful", ignoreCase = true)
                "🎙️ Classic Storytellers" -> char.tag.contains("Storyteller", ignoreCase = true) || char.tag.contains("Narrator", ignoreCase = true)
                "📚 Academic & Bard" -> char.tag.contains("Scholar", ignoreCase = true) || char.tag.contains("Bard", ignoreCase = true)
                "🧘 Lo-Fi & Sci-Fi" -> char.tag.contains("Lo-Fi", ignoreCase = true) || char.tag.contains("Cyberpunk", ignoreCase = true)
                "🎙️ Custom Cloned" -> char.isCustomCloned
                else -> true
            }

            matchesSearch && matchesTag
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F0B21)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Sheet Title & Live Switch Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Masked AI Voice Profiles",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "15+ Human Neural Characters • Real-time Switch Without Audio Pause",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA78BFA))
                    )
                }

                Button(
                    onClick = { showCloneModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("clone_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Clone Voice",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clone Voice",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, accent (e.g. Nigerian, Kid), or tag...", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFA78BFA)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_search_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterCategories) { tag ->
                    val isSelected = tag == selectedFilterTag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                ) else Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFEC4899) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedFilterTag = tag }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Characters Scroll List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredCharacters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No voice characters found for '$searchQuery'",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.6f))
                        )
                    }
                } else {
                    filteredCharacters.forEach { character ->
                        val isCurrentSelected = character.id == activeCharacter.id

                        CharacterCard(
                            character = character,
                            isSelected = isCurrentSelected,
                            cloudSynthesizer = cloudSynthesizer,
                            onSelect = {
                                VoiceCharacterManager.selectCharacter(character.id)
                                cloudSynthesizer?.clearCache()
                            },
                            onToggleFavorite = {
                                VoiceCharacterManager.toggleFavorite(character.id)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCloneModal) {
        VoiceCloneModalDialog(
            onDismiss = { showCloneModal = false },
            onSave = { name, desc, tag, emoji, voiceId ->
                VoiceCharacterManager.addCustomClonedVoice(name, desc, tag, emoji, voiceId)
                cloudSynthesizer?.clearCache()
                showCloneModal = false
            }
        )
    }
}

@Composable
private fun CharacterCard(
    character: VoiceCharacter,
    isSelected: Boolean,
    cloudSynthesizer: CloudVoiceSynthesizer?,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isAuditioning by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = if (isSelected) Brush.horizontalGradient(
                    listOf(Color(0xFF8B5CF6), Color(0xFF00F2FE))
                ) else Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() }
            .testTag("character_card_${character.id}"),
        color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Circle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Brush.linearGradient(
                            listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                        ) else Brush.linearGradient(
                            listOf(Color(0xFF1E1938), Color(0xFF2D2554))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = character.avatarEmoji,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = character.tag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFC084FC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Play Voice Sample Audition Button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isAuditioning) Color(0xFFEC4899) else Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    if (cloudSynthesizer != null && !isAuditioning) {
                                        isAuditioning = true
                                        val sampleText = "Hello! I am ${character.name}, your ${character.tag} reader. Let us read together."
                                        cloudSynthesizer.auditionVoiceSample(character.elevenLabsVoiceId, sampleText) {
                                            isAuditioning = false
                                        }
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAuditioning) Icons.Default.GraphicEq else Icons.Default.VolumeUp,
                                contentDescription = "Audition Voice Sample",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (character.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (character.isFavorite) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = character.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (isSelected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Active Reader • Real-time seamless voice switch ready",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCloneModalDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, tag: String, emoji: String, voiceId: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { com.example.tts.VoiceRecorder(context) }

    val isRecording by recorder.isRecording.collectAsState()
    val recordingSeconds by recorder.recordingSeconds.collectAsState()

    var recordedFile by remember { mutableStateOf<java.io.File?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("Custom Clone") }
    var emojiInput by remember { mutableStateOf("🎙️") }
    var voiceIdInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            recorder.stopRecording()
            onDismiss()
        },
        containerColor = Color(0xFF181233),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Instant 30s Voice Clone",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Record 30 seconds of speech or enter a custom ElevenLabs Voice ID to clone your voice:",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                )

                // 30s Mic Recorder Box
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isRecording) "🎙️ Recording Sample ($recordingSeconds / 30s)" else if (recordedFile != null) "✅ Sample Recorded (${recordedFile?.length()?.div(1024)} KB)" else "Press Record & speak naturally for 30 seconds",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) Color(0xFFEC4899) else if (recordedFile != null) Color(0xFF34D399) else Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { recordingSeconds / 30f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFEC4899),
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (isRecording) {
                                    recordedFile = recorder.stopRecording()
                                } else {
                                    recordedFile = recorder.startRecording(scope)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFEF4444) else Color(0xFF8B5CF6)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRecording) "Stop Recording" else "Start 30s Sample Record",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. My Custom Voice") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEC4899), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = voiceIdInput,
                    onValueChange = { voiceIdInput = it },
                    label = { Text("ElevenLabs Voice ID (Optional)") },
                    placeholder = { Text("e.g. 21m00Tcm4TlvDq8ikWAM") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEC4899), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = descInput,
                    onValueChange = { descInput = it },
                    label = { Text("Description") },
                    placeholder = { Text("Warm natural cloned voice...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEC4899), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    recorder.stopRecording()
                    val vId = voiceIdInput.ifBlank { "21m00Tcm4TlvDq8ikWAM" }
                    onSave(nameInput.ifBlank { "Cloned Reader" }, descInput.ifBlank { "Custom 30s recorded voice profile" }, tagInput, emojiInput, vId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
            ) {
                Text("Activate & Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                recorder.stopRecording()
                onDismiss()
            }) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
