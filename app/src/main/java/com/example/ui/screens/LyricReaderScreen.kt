package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.ParsedDocument
import com.example.parser.SentenceToken
import com.example.parser.WordToken
import com.example.tts.ReadingMode
import com.example.ui.theme.LyricThemePreset
import com.example.ui.viewmodel.LyricReaderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricReaderScreen(
    viewModel: LyricReaderViewModel,
    onBackClicked: () -> Unit
) {
    val activeDocEntity by viewModel.activeDocumentEntity.collectAsState()
    val parsedDoc by viewModel.parsedDocument.collectAsState()
    val playbackProgress by viewModel.playbackState.collectAsState()
    val enginePlaybackState by viewModel.enginePlaybackState.collectAsState()
    val currentTheme by viewModel.selectedLyricTheme.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val sleepTimerMins by viewModel.sleepTimerMinutesRemaining.collectAsState()
    val isAutoScrollEnabled by viewModel.isAutoScrollEnabled.collectAsState()
    val isBionicReadingEnabled by viewModel.isBionicReadingEnabled.collectAsState()
    val socraticAnswer by viewModel.socraticAnswer.collectAsState()
    val isSocraticLoading by viewModel.isSocraticLoading.collectAsState()
    val quizText by viewModel.quizText.collectAsState()
    val isQuizLoading by viewModel.isQuizLoading.collectAsState()

    val dynamicFormatMode by viewModel.dynamicFormatMode.collectAsState()
    val previouslyOnRecapText by viewModel.previouslyOnRecapText.collectAsState()
    val isRecapLoading by viewModel.isRecapLoading.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSleepTimerModal by remember { mutableStateOf(false) }
    var showTtsStylesSheet by remember { mutableStateOf(false) }
    var showAmbientMusicSheet by remember { mutableStateOf(false) }
    var showDiagnosticDashboard by remember { mutableStateOf(false) }
    var showSocraticSheet by remember { mutableStateOf(false) }
    var showBiomechanicalSheet by remember { mutableStateOf(false) }
    var showSentientSheet by remember { mutableStateOf(false) }
    var showAiVoiceLabHub by remember { mutableStateOf(false) }
    var showVoiceCharactersSheet by remember { mutableStateOf(false) }

    // Dynamic Estimated Time Remaining Calculation
    val totalWords = playbackProgress.totalWords
    val currentWord = playbackProgress.currentWordIndex
    val remainingWords = (totalWords - currentWord).coerceAtLeast(0)
    val effectiveWpm = (160f * playbackProgress.speedRate * playbackProgress.currentReadingMode.speechRate).coerceAtLeast(1f)
    val remainingSeconds = ((remainingWords / effectiveWpm) * 60).toInt()
    val remainingMin = remainingSeconds / 60
    val remainingSec = remainingSeconds % 60
    val timeRemainingText = if (remainingMin > 0) "${remainingMin}m ${remainingSec}s left" else "${remainingSec}s left"

    var isPlayerVisible by remember { mutableStateOf(true) }
    var isSmartAutoHideEnabled by remember { mutableStateOf(true) }

    // Smart Directional Scroll Auto-Hide: Scroll DOWN -> Hide, Scroll UP -> Show Controls
    LaunchedEffect(listState, isSmartAutoHideEnabled) {
        if (!isSmartAutoHideEnabled) {
            isPlayerVisible = true
            return@LaunchedEffect
        }
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.collect { (isScrolling, currentIndex, currentOffset) ->
            if (isScrolling) {
                val delta = (currentIndex - previousIndex) * 10000 + (currentOffset - previousScrollOffset)
                if (delta > 25) {
                    // Scrolling DOWN -> Hide Player Controls
                    isPlayerVisible = false
                } else if (delta < -25) {
                    // Scrolling UP -> Reveal Player Controls
                    isPlayerVisible = true
                }
            }
            previousIndex = currentIndex
            previousScrollOffset = currentOffset
        }
    }

    var hasJumpedToInitialPos by remember(activeDocEntity?.id) { mutableStateOf(false) }

    // Instant jump to saved reading position as soon as document and currentSentenceIndex are available
    LaunchedEffect(activeDocEntity?.id, parsedDoc?.title, playbackProgress.currentSentenceIndex) {
        if (!hasJumpedToInitialPos && parsedDoc != null && parsedDoc!!.sentences.isNotEmpty()) {
            val savedSentenceIndex = playbackProgress.currentSentenceIndex.coerceIn(0, (parsedDoc!!.sentences.size - 1).coerceAtLeast(0))
            listState.scrollToItem(savedSentenceIndex, scrollOffset = -220)
            hasJumpedToInitialPos = true
        }
    }

    // Auto-scroll list as reading progresses while active
    LaunchedEffect(playbackProgress.currentSentenceIndex, isAutoScrollEnabled) {
        if (parsedDoc != null && parsedDoc!!.sentences.isNotEmpty()) {
            val targetIndex = playbackProgress.currentSentenceIndex.coerceIn(0, (parsedDoc!!.sentences.size - 1).coerceAtLeast(0))
            if (playbackProgress.isPlaying && isAutoScrollEnabled) {
                listState.animateScrollToItem(targetIndex, scrollOffset = -220)
            }
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(currentTheme.backgroundStart, currentTheme.backgroundEnd)
    )

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundBrush)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top Navigation Header (Polished iOS/M3 Clean Layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.saveProgressToDb()
                            onBackClicked()
                        },
                        modifier = Modifier.testTag("lyric_reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = activeDocEntity?.title ?: "Document",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🎭 ${playbackProgress.currentReadingMode.title} Style",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = currentTheme.activePill,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { showTtsStylesSheet = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.addCurrentBookmark() },
                            modifier = Modifier.testTag("add_bookmark_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Add Bookmark",
                                tint = Color.White
                            )
                        }

                        // Glowing Unified AI & Voice Hub Launch Button
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF00F2FE))
                                    )
                                )
                                .clickable { showAiVoiceLabHub = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("ai_voice_lab_hub_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Voice Lab",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AI Hub",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("lyric_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Interactive Top Canvas Waveform Progress & Spectrum Visualizer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${playbackProgress.currentWordIndex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    com.example.ui.components.CanvasWaveformVisualizer(
                        progressPercentage = playbackProgress.progressPercentage,
                        isPlaying = playbackProgress.isPlaying,
                        audioAmplitudes = playbackProgress.visualizerAmplitudes,
                        activeColor = currentTheme.activePill,
                        inactiveColor = Color.White.copy(alpha = 0.18f),
                        barCount = 42,
                        onSeek = { fraction ->
                            val targetWord = (fraction * playbackProgress.totalWords).toInt()
                            viewModel.speechEngine.seekToWord(targetWord)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${playbackProgress.totalWords} w",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Aura Dynamic Format Shifting Bar
                AuraFormatSelectorBar(
                    currentMode = dynamicFormatMode,
                    onModeSelected = { viewModel.setDynamicFormatMode(it) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (parsedDoc != null) {
                    val doc = parsedDoc!!
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxHeight()
                        ) {
                            when (dynamicFormatMode) {
                                com.example.tts.DynamicFormatMode.KINETIC_SPRINT -> {
                                    KineticSprintReaderView(
                                        parsedDoc = doc,
                                        playbackProgress = playbackProgress,
                                        onWordSeek = { wordIdx -> viewModel.speechEngine.seekToWord(wordIdx) }
                                    )
                                }
                                com.example.tts.DynamicFormatMode.DIALOGUE_ONLY -> {
                                    val dialogueSentences = remember(doc) {
                                        doc.sentences.filter { it.text.contains("\"") || it.text.contains("“") || it.text.contains("'") }
                                    }
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(24.dp)
                                    ) {
                                        item {
                                            Text(
                                                text = "🎭 DIALOGUE SCRIPT MODE (${dialogueSentences.size} interactions)",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFEC4899), fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        items(if (dialogueSentences.isNotEmpty()) dialogueSentences else doc.sentences, key = { it.sentenceIndex }) { sentenceToken ->
                                            SentenceLyricRow(
                                                sentence = sentenceToken,
                                                currentWordIndex = playbackProgress.currentWordIndex,
                                                currentSentenceIndex = playbackProgress.currentSentenceIndex,
                                                wordSyncProgress = playbackProgress.wordSyncProgress,
                                                fontSizeSp = fontSizeSp,
                                                theme = currentTheme,
                                                isBionicReadingEnabled = isBionicReadingEnabled,
                                                onWordClicked = { wordIdx ->
                                                    viewModel.speechEngine.seekToWord(wordIdx)
                                                }
                                            )
                                        }
                                        item {
                                            Spacer(modifier = Modifier.height(220.dp))
                                        }
                                    }
                                }
                                com.example.tts.DynamicFormatMode.PODCAST_DEBATE -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1436)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(18.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Podcast", tint = Color(0xFF8B5CF6))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("🎙️ AI PODCAST DEBATE MODE", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        "Host A & Host B synthesize this chapter's key themes into a dynamic conversational podcast breakdown.",
                                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Button(
                                                        onClick = {
                                                            viewModel.generatePodcastScript()
                                                            showSocraticSheet = true
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Text("Generate 2-Host Podcast Script")
                                                    }
                                                }
                                            }
                                        }
                                        items(doc.sentences, key = { it.sentenceIndex }) { sentenceToken ->
                                            SentenceLyricRow(
                                                sentence = sentenceToken,
                                                currentWordIndex = playbackProgress.currentWordIndex,
                                                currentSentenceIndex = playbackProgress.currentSentenceIndex,
                                                wordSyncProgress = playbackProgress.wordSyncProgress,
                                                fontSizeSp = fontSizeSp,
                                                theme = currentTheme,
                                                isBionicReadingEnabled = isBionicReadingEnabled,
                                                onWordClicked = { wordIdx ->
                                                    viewModel.speechEngine.seekToWord(wordIdx)
                                                }
                                            )
                                        }
                                        item {
                                            Spacer(modifier = Modifier.height(220.dp))
                                        }
                                    }
                                }
                                else -> { // DEEP_DIVE
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(24.dp)
                                    ) {
                                        items(doc.sentences, key = { it.sentenceIndex }) { sentenceToken ->
                                            SentenceLyricRow(
                                                sentence = sentenceToken,
                                                currentWordIndex = playbackProgress.currentWordIndex,
                                                currentSentenceIndex = playbackProgress.currentSentenceIndex,
                                                wordSyncProgress = playbackProgress.wordSyncProgress,
                                                fontSizeSp = fontSizeSp,
                                                theme = currentTheme,
                                                isBionicReadingEnabled = isBionicReadingEnabled,
                                                onWordClicked = { wordIdx ->
                                                    viewModel.speechEngine.seekToWord(wordIdx)
                                                }
                                            )
                                        }

                                        item {
                                            Spacer(modifier = Modifier.height(220.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = "Loading lyrics...", color = Color.White)
                    }
                }
            }

            // Bottom Player Control Dock (Intelligent Auto-Hide on Scroll Down, Reveal on Scroll Up or Tap)
            AnimatedVisibility(
                visible = isPlayerVisible || !isSmartAutoHideEnabled,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlayerControlDock(
                    viewModel = viewModel,
                    playbackProgress = playbackProgress,
                    theme = currentTheme,
                    sleepTimerMins = sleepTimerMins,
                    isSmartAutoHideEnabled = isSmartAutoHideEnabled,
                    onToggleSmartAutoHide = { isSmartAutoHideEnabled = !isSmartAutoHideEnabled },
                    onOpenSleepTimer = { showSleepTimerModal = true },
                    onOpenTtsStyles = { showTtsStylesSheet = true }
                )
            }
        }
    }

    // TTS Styles Selection Bottom Sheet
    if (showTtsStylesSheet) {
        TtsStylesModalBottomSheet(
            currentReadingMode = playbackProgress.currentReadingMode,
            theme = currentTheme,
            onSelectMode = { mode -> viewModel.speechEngine.setReadingMode(mode) },
            onDismiss = { showTtsStylesSheet = false }
        )
    }

    val atmosphereTheme by viewModel.atmosphereTheme.collectAsState()

    // Ambient Music Bottom Sheet
    if (showAmbientMusicSheet) {
        AmbientMusicModalBottomSheet(
            currentTrack = playbackProgress.ambientTrack,
            currentVolume = playbackProgress.ambientVolume,
            lowPassRatio = playbackProgress.lowPassRatio,
            currentAtmosphereTheme = atmosphereTheme,
            theme = currentTheme,
            onSelectTrack = { track -> viewModel.setAmbientTrack(track) },
            onSelectAtmosphereTheme = { atmo -> viewModel.setAtmosphereTheme(atmo) },
            onVolumeChange = { vol -> viewModel.setAmbientVolume(vol) },
            onLowPassChange = { ratio -> viewModel.setLowPassRatio(ratio) },
            onDismiss = { showAmbientMusicSheet = false }
        )
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF130E22)
        ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Application & Reader Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Global App Theme Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Global App Dark Theme Mode",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (isDarkTheme) "Dark Canvas (OLED)" else "Light Mode (High Contrast Bright)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }

                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { viewModel.setDarkTheme(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = currentTheme.activePill,
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bionic ADHD Focus Reading Engine Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bionic ADHD Focus Mode",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            )
                            Text(
                                text = if (isBionicReadingEnabled) "Bold initial letters for 3x faster eye fixations & neurodivergent concentration" else "Standard uniform font rendering",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }

                        Switch(
                            checked = isBionicReadingEnabled,
                            onCheckedChange = { viewModel.toggleBionicReading() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Scroll Karaoke View Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Scroll Karaoke View",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (isAutoScrollEnabled) "Automatically follow text highlighting as reading progresses" else "Manual scroll mode active",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }

                        Switch(
                            checked = isAutoScrollEnabled,
                            onCheckedChange = { viewModel.setAutoScrollEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = currentTheme.activePill,
                                uncheckedThumbColor = Color.DarkGray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Word-Sync Timing Offset Calibration Slider
                    val currentOffset = playbackProgress.syncOffsetMs
                    val offsetLabel = when {
                        currentOffset > 0 -> "+${currentOffset}ms (Earlier Highlight)"
                        currentOffset < 0 -> "${currentOffset}ms (Later Highlight)"
                        else -> "0ms (Default Sync)"
                    }
                    Text(
                        text = "Word-Sync Timing Calibration",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Calibrate latency offset ($offsetLabel) to match your device TTS engine.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = currentOffset.toFloat(),
                        onValueChange = { viewModel.setSyncOffsetMs(it.toInt()) },
                        valueRange = -250f..250f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = currentTheme.activePill,
                            activeTrackColor = currentTheme.activePill
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sleep Timer Section in Settings
                    Text(
                        text = "Sleep Timer Schedule",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = if (sleepTimerMins != null) "Active: Pausing in ${sleepTimerMins}m" else "Schedule reader to pause automatically",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (sleepTimerMins != null) currentTheme.activePill else Color.White.copy(alpha = 0.7f)
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { min ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sleepTimerMins == min) currentTheme.activePill else Color.White.copy(alpha = 0.08f))
                                    .clickable { viewModel.startSleepTimer(min) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${min}m",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                    if (sleepTimerMins != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.cancelSleepTimer() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Turn Off Timer", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // TTS Engine Source Selection
                    Text(
                        text = "Speech Synthesis Engine Source",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Switch between Google Device Native TTS (0 credits, offline) and AI Cloud Voice with auto-fallback when credits finish.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    com.example.tts.TtsEngineType.entries.forEach { engine ->
                        val isSelected = playbackProgress.engineType == engine
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.setTtsEngineType(engine) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) currentTheme.activePill.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) currentTheme.activePill else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = if (isSelected) currentTheme.activePill else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = engine.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = engine.description,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Font Size Adjuster
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Lyric Font Size (${fontSizeSp}sp)", color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.setFontSizeSp(fontSizeSp - 2) }) {
                                Text("-", fontSize = 24.sp, color = Color.White)
                            }
                            Icon(Icons.Default.FormatSize, contentDescription = null, tint = Color.White)
                            TextButton(onClick = { viewModel.setFontSizeSp(fontSizeSp + 2) }) {
                                Text("+", fontSize = 24.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Theme Preset Selection
                    Text(text = "Lyrics Canvas Theme", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricThemePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = currentTheme == preset,
                                onClick = { viewModel.setLyricTheme(preset) },
                                label = { Text(preset.title) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = preset.activePill,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val isMaskedAiActive = playbackProgress.engineType == com.example.tts.TtsEngineType.MASKED_AI_VOICE
                    if (isMaskedAiActive) {
                        val activeChar by com.example.tts.VoiceCharacterManager.selectedCharacter.collectAsState()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.18f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSettingsSheet = false
                                    showVoiceCharactersSheet = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = activeChar.avatarEmoji, fontSize = 26.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AI Character: ${activeChar.name}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                    Text(
                                        text = "Pitch, Tone & Prosody automatically governed by ${activeChar.tag}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEC4899))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Switch",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    )
                                }
                            }
                        }
                    } else {
                        // Voice Pitch Control for Device Native Speech Engine
                        Text(
                            text = "Pitch & Tone Accent (${String.format("%.2f", playbackProgress.pitchMultiplier)}x)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = playbackProgress.pitchMultiplier,
                            onValueChange = { viewModel.speechEngine.setPitch(it) },
                            valueRange = 0.7f..1.4f,
                            colors = SliderDefaults.colors(
                                thumbColor = currentTheme.activePill,
                                activeTrackColor = currentTheme.activePill
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
        }
    }

    // Sleep Timer Modal Sheet
    if (showSleepTimerModal) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerModal = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF130E22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Automatically pause reading after specified duration",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )

                Spacer(modifier = Modifier.height(20.dp))

                val options = listOf(5, 15, 30, 45, 60)
                options.forEach { min ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                viewModel.startSleepTimer(min)
                                showSleepTimerModal = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1735)
                    ) {
                        Text(
                            text = "$min Minutes",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (sleepTimerMins != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            viewModel.cancelSleepTimer()
                            showSleepTimerModal = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel Sleep Timer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // Socratic AI Co-Pilot Modal Sheet
    if (showSocraticSheet) {
        SocraticCoPilotModalBottomSheet(
            viewModel = viewModel,
            parsedDoc = parsedDoc,
            playbackProgress = playbackProgress,
            socraticAnswer = socraticAnswer,
            isSocraticLoading = isSocraticLoading,
            quizText = quizText,
            isQuizLoading = isQuizLoading,
            previouslyOnRecapText = previouslyOnRecapText,
            isRecapLoading = isRecapLoading,
            theme = currentTheme,
            onDismiss = { showSocraticSheet = false }
        )
    }

    if (showBiomechanicalSheet) {
        BiomechanicalVoiceModalBottomSheet(
            engine = viewModel.biomechanicalEngine,
            onDismiss = { showBiomechanicalSheet = false }
        )
    }

    if (showSentientSheet) {
        SentientArticulationModalBottomSheet(
            engine = viewModel.sentientArticulationEngine,
            currentSentenceIndex = playbackProgress.currentSentenceIndex,
            onDismiss = { showSentientSheet = false }
        )
    }

    if (showAiVoiceLabHub) {
        AiVoiceLabModalBottomSheet(
            onOpenVoiceCharacters = { showVoiceCharactersSheet = true },
            onOpenPodcast = {
                viewModel.setDynamicFormatMode(com.example.tts.DynamicFormatMode.PODCAST_DEBATE)
                viewModel.askSocraticCompanion("Turn this current section into a lively, 2-host conversational podcast debate script.")
                showSocraticSheet = true
            },
            onOpenSprint = {
                viewModel.setDynamicFormatMode(com.example.tts.DynamicFormatMode.KINETIC_SPRINT)
            },
            onOpenSocratic = { showSocraticSheet = true },
            onOpenSentient = { showSentientSheet = true },
            onOpenBiomechanical = { showBiomechanicalSheet = true },
            onOpenAmbientMusic = { showAmbientMusicSheet = true },
            onOpenDiagnostics = { showDiagnosticDashboard = true },
            onDismiss = { showAiVoiceLabHub = false }
        )
    }

    if (showVoiceCharactersSheet) {
        VoiceCharactersModalBottomSheet(
            cloudSynthesizer = viewModel.speechEngine.cloudVoiceSynthesizer,
            onDismiss = { showVoiceCharactersSheet = false }
        )
    }

    if (showDiagnosticDashboard) {
        TtsDiagnosticDashboardModal(
            engineState = enginePlaybackState,
            onDismiss = { showDiagnosticDashboard = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceLyricRow(
    sentence: SentenceToken,
    currentWordIndex: Int,
    currentSentenceIndex: Int,
    wordSyncProgress: Float,
    fontSizeSp: Int,
    theme: LyricThemePreset,
    isBionicReadingEnabled: Boolean = true,
    onWordClicked: (Int) -> Unit
) {
    val isPastSentence = sentence.sentenceIndex < currentSentenceIndex
    val isCurrentSentence = sentence.sentenceIndex == currentSentenceIndex

    FlowRow(
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lyric_sentence_row_${sentence.sentenceIndex}")
    ) {
        sentence.words.forEach { wordToken ->
            val isCurrentWord = wordToken.wordIndex == currentWordIndex
            val isPastWord = wordToken.wordIndex < currentWordIndex

            val targetColor = when {
                isCurrentWord -> theme.activeText
                isPastWord -> theme.activeText.copy(alpha = 0.95f)
                isCurrentSentence -> Color.White.copy(alpha = 0.90f)
                isPastSentence -> theme.textMuted.copy(alpha = 0.50f)
                else -> theme.textMuted.copy(alpha = 0.35f)
            }

            val textColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(120),
                label = "textColor"
            )

            val pillBgColor by animateColorAsState(
                targetValue = if (isCurrentWord) theme.activePill else Color.Transparent,
                animationSpec = tween(150),
                label = "pillBgColor"
            )

            val scaleFactor by animateFloatAsState(
                targetValue = if (isCurrentWord) 1.08f else 1.0f,
                animationSpec = tween(150),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 3.dp)
                    .scale(scaleFactor)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pillBgColor)
                    .clickable { onWordClicked(wordToken.wordIndex) }
                    .padding(horizontal = if (isCurrentWord) 8.dp else 4.dp, vertical = 2.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val wordStr = wordToken.word
                    if (isBionicReadingEnabled && wordStr.length >= 2) {
                        val fixationLength = (wordStr.length * 0.45f).roundToInt().coerceIn(1, wordStr.length - 1)
                        val boldPrefix = wordStr.take(fixationLength)
                        val normalSuffix = wordStr.drop(fixationLength)

                        val annotated = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = if (isCurrentWord) FontWeight.Black else FontWeight.ExtraBold, color = textColor)) {
                                append(boldPrefix)
                            }
                            withStyle(SpanStyle(fontWeight = if (isCurrentWord) FontWeight.Medium else FontWeight.Normal, color = if (isCurrentWord) textColor else textColor.copy(alpha = 0.8f))) {
                                append(normalSuffix)
                            }
                        }

                        Text(
                            text = annotated,
                            fontSize = if (isCurrentWord) (fontSizeSp + 2).sp else fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45).sp
                        )
                    } else {
                        Text(
                            text = wordStr,
                            fontSize = if (isCurrentWord) (fontSizeSp + 2).sp else fontSizeSp.sp,
                            fontWeight = if (isCurrentWord) FontWeight.ExtraBold else if (isCurrentSentence) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            lineHeight = (fontSizeSp * 1.45).sp
                        )
                    }

                    if (isCurrentWord) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width((wordToken.word.length * (fontSizeSp * 0.55)).dp.coerceAtLeast(16.dp))
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(wordSyncProgress.coerceIn(0.05f, 1f))
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(theme.activeText)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    amplitudes: List<Float>,
    isPlaying: Boolean,
    activeColor: Color
) {
    val breathTransition = rememberInfiniteTransition(label = "waveform_breath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_breath_scale"
    )

    val averageAmp = if (amplitudes.isNotEmpty()) amplitudes.average().toFloat() else 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(20.dp)
                    .scale(breathScale * (0.95f + averageAmp * 0.15f))
                    .blur(12.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                activeColor.copy(alpha = 0.15f),
                                Color(0xFFEC4899).copy(alpha = 0.25f),
                                activeColor.copy(alpha = 0.15f)
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            amplitudes.forEachIndexed { idx, amp ->
                val animatedAmp by animateFloatAsState(
                    targetValue = if (isPlaying) amp else 0.15f,
                    animationSpec = tween(100),
                    label = "visualizer_bar_$idx"
                )

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((24 * animatedAmp).dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) Brush.verticalGradient(
                                listOf(activeColor, Color(0xFFEC4899))
                            ) else SolidColor(activeColor.copy(alpha = 0.3f))
                        )
                )
            }
        }
    }
}

@Composable
fun PlayerControlDock(
    modifier: Modifier = Modifier,
    viewModel: LyricReaderViewModel,
    playbackProgress: com.example.tts.PlaybackProgress,
    theme: LyricThemePreset,
    sleepTimerMins: Int?,
    isSmartAutoHideEnabled: Boolean = true,
    onToggleSmartAutoHide: (() -> Unit)? = null,
    onOpenSleepTimer: () -> Unit,
    onOpenTtsStyles: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF130E22).copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick-Switch Favorite Voice Profile Filter Row
            val allVoices by com.example.tts.VoiceCharacterManager.characters.collectAsState()
            val activeVoice by com.example.tts.VoiceCharacterManager.selectedCharacter.collectAsState()
            val favoriteVoices = remember(allVoices) { allVoices.filter { it.isFavorite } }

            if (favoriteVoices.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⭐ Favorites:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    favoriteVoices.forEach { favChar ->
                        val isSelected = favChar.id == activeVoice.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                    ) else androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFEC4899) else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    com.example.tts.VoiceCharacterManager.selectCharacter(favChar.id)
                                    viewModel.speechEngine.cloudVoiceSynthesizer.clearCache()
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = favChar.avatarEmoji,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = favChar.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Mode Selector Pill Chips & TTS Style Expand Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReadingMode.entries.forEach { mode ->
                        val isSelected = playbackProgress.currentReadingMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) theme.activePill else Color.White.copy(alpha = 0.08f))
                                .clickable { viewModel.speechEngine.setReadingMode(mode) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onToggleSmartAutoHide != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSmartAutoHideEnabled) theme.activePill.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (isSmartAutoHideEnabled) theme.activePill else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { onToggleSmartAutoHide() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSmartAutoHideEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Auto-Hide",
                                    tint = if (isSmartAutoHideEnabled) theme.activePill else Color.Gray,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isSmartAutoHideEnabled) "Auto-Hide" else "Pinned",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSmartAutoHideEnabled) theme.activePill else Color.Gray,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { onOpenTtsStyles() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Styles",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = theme.activePill
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Styles",
                                tint = theme.activePill,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Sleek Word Count & Reading Progress Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Word ${playbackProgress.currentWordIndex + 1} of ${playbackProgress.totalWords}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                )

                Text(
                    text = "${(playbackProgress.progressPercentage * 100).toInt()}% completed",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = theme.activePill,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Audio Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleep Timer Trigger
                IconButton(onClick = onOpenSleepTimer) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerMins != null) theme.activePill else Color.White
                    )
                }

                // Jump -10s
                IconButton(
                    onClick = { viewModel.speechEngine.jumpBackward(10) },
                    modifier = Modifier.testTag("jump_backward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Jump -10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Main Play / Pause FAB
                FloatingActionButton(
                    onClick = {
                        if (playbackProgress.isPlaying) {
                            viewModel.speechEngine.pause()
                        } else {
                            viewModel.speechEngine.play()
                        }
                    },
                    containerColor = theme.activePill,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(60.dp)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackProgress.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackProgress.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Jump +10s
                IconButton(
                    onClick = { viewModel.speechEngine.jumpForward(10) },
                    modifier = Modifier.testTag("jump_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Jump +10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Speed Selector Pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable {
                            val nextSpeed = when (playbackProgress.speedRate) {
                                0.75f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 0.75f
                            }
                            viewModel.speechEngine.setSpeed(nextSpeed)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("speed_button")
                ) {
                    Text(
                        text = "${playbackProgress.speedRate}x",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsStylesModalBottomSheet(
    currentReadingMode: ReadingMode,
    theme: LyricThemePreset,
    onSelectMode: (ReadingMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color(0xFF130E22)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "TTS Voice Styles & Modes",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = "Select a voice tone style to adjust pitch, speed rate, and pause dynamics.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.7f)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadingMode.entries.forEach { mode ->
                    val isSelected = currentReadingMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectMode(mode)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) theme.activePill.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) theme.activePill else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = theme.activePill,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${mode.speechRate}x rate",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = theme.activePill
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pitch: ${mode.pitch}x",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientMusicModalBottomSheet(
    currentTrack: com.example.tts.AmbientTrack,
    currentVolume: Float,
    lowPassRatio: Float,
    currentAtmosphereTheme: com.example.tts.AtmosphereTheme,
    theme: LyricThemePreset,
    onSelectTrack: (com.example.tts.AmbientTrack) -> Unit,
    onSelectAtmosphereTheme: (com.example.tts.AtmosphereTheme) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLowPassChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color(0xFF130E22)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = theme.activePill,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Audio Atmosphere & Ambient Library",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Cross-fading instrumental themes & auto-ducking audio mixer",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Atmosphere Reading Themes",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.example.tts.AtmosphereTheme.entries.forEach { atmoTheme ->
                    val isSelected = currentAtmosphereTheme == atmoTheme
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectAtmosphereTheme(atmoTheme) },
                        label = {
                            Text(
                                text = atmoTheme.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.activePill,
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.White.copy(alpha = 0.15f),
                            selectedBorderColor = theme.activePill
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Ambient Volume: ${(currentVolume * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
            Slider(
                value = currentVolume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.activePill,
                    activeTrackColor = theme.activePill,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ambient_volume_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Low-Pass Acoustic Filter: ${(lowPassRatio * 100).toInt()}% Cutoff",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
            Text(
                text = "Attenuates sharp highs for warm acoustic ambience",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f)
                )
            )
            Slider(
                value = lowPassRatio,
                onValueChange = onLowPassChange,
                valueRange = 0.05f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.activePill,
                    activeTrackColor = theme.activePill,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ambient_lowpass_slider")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Instrumental Track",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.example.tts.AmbientTrack.entries.forEach { track ->
                    val isSelected = currentTrack == track
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTrack(track) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) theme.activePill.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) theme.activePill else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = track.displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                     if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = theme.activePill,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = track.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocraticCoPilotModalBottomSheet(
    viewModel: LyricReaderViewModel,
    parsedDoc: ParsedDocument?,
    playbackProgress: com.example.tts.PlaybackProgress,
    socraticAnswer: String?,
    isSocraticLoading: Boolean,
    quizText: String?,
    isQuizLoading: Boolean,
    previouslyOnRecapText: String? = null,
    isRecapLoading: Boolean = false,
    theme: LyricThemePreset,
    onDismiss: () -> Unit
) {
    var customQuery by remember { mutableStateOf("") }
    val currentSentenceIndex = playbackProgress.currentSentenceIndex
    val activeSentence = parsedDoc?.sentences?.getOrNull(currentSentenceIndex)?.text ?: "No active sentence selected."

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F0A1C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Socratic AI",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Socratic AI Co-Pilot",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Active Comprehension & Neural Recall Coach",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFA78BFA))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Context Sentence Box
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ACTIVE READING SNIPPET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$activeSentence\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Socratic Action Chips
            Text(
                text = "1-Tap Socratic Exploration",
                style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { viewModel.askSocraticCompanion("Explain this active sentence like I'm 5 using a vivid real-world analogy.") },
                    label = { Text("💡 Explain (ELI5)", color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.25f))
                )
                FilterChip(
                    selected = false,
                    onClick = { viewModel.askSocraticCompanion("Give me 3 punchy, unforgettable takeaways from this concept.") },
                    label = { Text("🎯 3 Takeaways", color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFEC4899).copy(alpha = 0.25f))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { viewModel.generatePreviouslyOnRecap() },
                    label = { Text("🎬 'Previously On...' Recap", color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF00F2FE).copy(alpha = 0.25f))
                )
                FilterChip(
                    selected = false,
                    onClick = { viewModel.askSocraticCompanion("Wait, who is this character and what was their previous conflict?") },
                    label = { Text("🙋 'Wait, Who?'", color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.25f))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { viewModel.generateQuizForCurrentSection() },
                    label = { Text("🧠 Active Recall Quiz", color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF10B981).copy(alpha = 0.25f))
                )
                FilterChip(
                    selected = false,
                    onClick = { viewModel.askSocraticCompanion("Why is this concept crucial and how does it connect to real life?") },
                    label = { Text("🤔 Why it matters", color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF38BDF8).copy(alpha = 0.25f))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Question Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    placeholder = { Text("Ask Socratic AI anything about this book...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFA78BFA),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (customQuery.isNotBlank()) {
                            viewModel.askSocraticCompanion(customQuery)
                            customQuery = ""
                        }
                    },
                    containerColor = Color(0xFF8B5CF6),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Send")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AI Loading state or Answer Box
            if (isSocraticLoading || isQuizLoading || isRecapLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = Color(0xFFA78BFA), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isQuizLoading) "Generating Active Recall Quiz..." else if (isRecapLoading) "Constructing 'Previously On...' Recap..." else "Synthesizing Socratic Insight...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                val activeOutput = socraticAnswer ?: quizText ?: previouslyOnRecapText
                if (!activeOutput.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1436)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (quizText != null) "🧠 MEMORY PALACE ACTIVE RECALL QUIZ" else "💡 SOCRATIC AI INSIGHT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFA78BFA),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )

                                TextButton(
                                    onClick = {
                                        viewModel.speechEngine.speakRawText(activeOutput)
                                    }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Read Aloud",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Read Aloud", color = Color(0xFF10B981), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = activeOutput,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
