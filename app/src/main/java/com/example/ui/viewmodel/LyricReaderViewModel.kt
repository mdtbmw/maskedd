package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BookmarkEntity
import com.example.data.DocumentEntity
import com.example.data.DocumentRepository
import com.example.parser.DocumentParser
import com.example.parser.ParsedDocument
import com.example.tts.PlaybackProgress
import com.example.tts.ReadingMode
import com.example.tts.SpeechEngine
import com.example.ui.theme.LyricThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LyricReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    val speechEngine: SpeechEngine

    val allDocuments: StateFlow<List<DocumentEntity>>
    val allBookmarks: StateFlow<List<BookmarkEntity>>

    private val _activeDocumentEntity = MutableStateFlow<DocumentEntity?>(null)
    val activeDocumentEntity: StateFlow<DocumentEntity?> = _activeDocumentEntity.asStateFlow()

    private val _parsedDocument = MutableStateFlow<ParsedDocument?>(null)
    val parsedDocument: StateFlow<ParsedDocument?> = _parsedDocument.asStateFlow()

    val playbackState: StateFlow<PlaybackProgress>
    val enginePlaybackState: StateFlow<com.example.tts.EnginePlaybackState>
    val atmosphereTheme: StateFlow<com.example.tts.AtmosphereTheme>

    private val _selectedLyricTheme = MutableStateFlow(LyricThemePreset.SPOTIFY_DARK)
    val selectedLyricTheme: StateFlow<LyricThemePreset> = _selectedLyricTheme.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isAutoScrollEnabled = MutableStateFlow(true)
    val isAutoScrollEnabled: StateFlow<Boolean> = _isAutoScrollEnabled.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(24)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _sleepTimerMinutesRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesRemaining: StateFlow<Int?> = _sleepTimerMinutesRemaining.asStateFlow()

    private var sleepTimerJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    init {
        com.example.ai.ApiKeyManager.initialize(application)
        val database = AppDatabase.getDatabase(application)
        repository = DocumentRepository(database.documentDao(), database.bookmarkDao())
        speechEngine = SpeechEngine(application, viewModelScope)
        playbackState = speechEngine.progressState
        enginePlaybackState = speechEngine.engineState
        atmosphereTheme = speechEngine.audioAtmosphereService.currentTheme

        val docsFlow = MutableStateFlow<List<DocumentEntity>>(emptyList())
        allDocuments = docsFlow.asStateFlow()

        val bookmarksFlow = MutableStateFlow<List<BookmarkEntity>>(emptyList())
        allBookmarks = bookmarksFlow.asStateFlow()

        viewModelScope.launch(Dispatchers.IO) {
            repository.allDocuments.collectLatest { docs ->
                if (docs.isEmpty()) {
                    // Populate initial sample documents on first launch
                    val samples = DocumentParser.getInitialSampleDocuments()
                    for (sample in samples) {
                        repository.insertDocument(sample)
                    }
                } else {
                    docsFlow.value = docs
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.allBookmarks.collectLatest { bks ->
                bookmarksFlow.value = bks
            }
        }

        com.example.service.TtsPlaybackService.speechEngine = speechEngine

        viewModelScope.launch {
            playbackState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { isPlaying ->
                    val context = getApplication<Application>()
                    val intent = Intent(context, com.example.service.TtsPlaybackService::class.java)
                    if (isPlaying) {
                        intent.action = com.example.service.TtsPlaybackService.ACTION_PLAY
                        androidx.core.content.ContextCompat.startForegroundService(context, intent)
                    }
                }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
    }

    fun setAutoScrollEnabled(enabled: Boolean) {
        _isAutoScrollEnabled.value = enabled
    }

    fun setSyncOffsetMs(offsetMs: Int) {
        speechEngine.setSyncOffsetMs(offsetMs)
    }

    fun setAmbientTrack(track: com.example.tts.AmbientTrack) {
        speechEngine.setAmbientTrack(track)
    }

    fun setAtmosphereTheme(theme: com.example.tts.AtmosphereTheme) {
        speechEngine.audioAtmosphereService.setTheme(theme)
    }

    fun setAmbientVolume(vol: Float) {
        speechEngine.setAmbientVolume(vol)
    }

    fun setLowPassRatio(ratio: Float) {
        speechEngine.setLowPassRatio(ratio)
    }

    fun cleanMemory() {
        com.example.parser.MemoryMonitor.checkAndCleanMemory(forceGc = true)
    }

    fun setTtsEngineType(engineType: com.example.tts.TtsEngineType) {
        speechEngine.setEngineType(engineType)
    }

    fun setFishAudioKey(key: String) {
        com.example.ai.ApiKeyManager.setFishAudioKey(key)
    }

    fun setElevenLabsKey(key: String) {
        com.example.ai.ApiKeyManager.setElevenLabsKey(key)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun openDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _activeDocumentEntity.value = document
                com.example.service.TtsPlaybackService.activeDocumentTitle = document.title

                val parsed = DocumentParser.processText(
                    title = document.title,
                    subtitle = document.subtitle,
                    format = document.format,
                    rawContent = document.content
                )
                _parsedDocument.value = parsed

                val initialMode = ReadingMode.fromName(document.defaultReadingMode)
                speechEngine.setReadingMode(initialMode)
                speechEngine.loadDocument(parsed, document.lastReadWordIndex)

                // Update last read time in repository
                repository.updateReadingProgress(document.id, document.lastReadWordIndex, document.lastReadSentenceIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importDocumentFromUri(uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = DocumentParser.parseUri(getApplication(), uri, fileName)
                val safeText = if (parsed.rawText.length > 2_000_000) parsed.rawText.substring(0, 2_000_000) else parsed.rawText
                val newEntity = DocumentEntity(
                    title = parsed.title,
                    subtitle = parsed.subtitle,
                    format = parsed.format,
                    content = safeText,
                    wordCount = parsed.words.size,
                    category = "User Import",
                    coverGradientStart = 0xFF06B6D4,
                    coverGradientEnd = 0xFF3B82F6
                )
                val id = repository.insertDocument(newEntity)
                val insertedDoc = repository.getDocumentById(id)
                if (insertedDoc != null) {
                    openDocument(insertedDoc)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createDocumentFromText(title: String, format: String, text: String, category: String = "User Text") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = DocumentParser.processText(title, "Custom $format Document", format, text)
                val safeText = if (text.length > 2_000_000) text.substring(0, 2_000_000) else text
                val newEntity = DocumentEntity(
                    title = title,
                    subtitle = "Custom $format Document • ${parsed.words.size} words",
                    format = format,
                    content = safeText,
                    wordCount = parsed.words.size,
                    category = category,
                    coverGradientStart = 0xFFEC4899,
                    coverGradientEnd = 0xFF8B5CF6
                )
                val id = repository.insertDocument(newEntity)
                val insertedDoc = repository.getDocumentById(id)
                if (insertedDoc != null) {
                    openDocument(insertedDoc)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveProgressToDb() {
        val activeDoc = _activeDocumentEntity.value ?: return
        val currentProgress = playbackState.value
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateReadingProgress(
                id = activeDoc.id,
                wordIndex = currentProgress.currentWordIndex,
                sentenceIndex = currentProgress.currentSentenceIndex
            )
        }
    }

    fun toggleFavorite(documentId: Long, currentIsFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(documentId, !currentIsFavorite)
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDocument(documentId)
            if (_activeDocumentEntity.value?.id == documentId) {
                speechEngine.stop()
                _activeDocumentEntity.value = null
                _parsedDocument.value = null
            }
        }
    }

    fun addCurrentBookmark(note: String = "") {
        val activeDoc = _activeDocumentEntity.value ?: return
        val progress = playbackState.value
        val parsed = _parsedDocument.value ?: return
        val sentence = parsed.sentences.getOrNull(progress.currentSentenceIndex)?.text ?: progress.currentWord

        viewModelScope.launch(Dispatchers.IO) {
            repository.addBookmark(
                BookmarkEntity(
                    documentId = activeDoc.id,
                    wordIndex = progress.currentWordIndex,
                    sentenceText = sentence,
                    note = note
                )
            )
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookmark(bookmarkId)
        }
    }

    val biomechanicalEngine = com.example.tts.BiomechanicalVoiceEngine()
    val sentientArticulationEngine = com.example.tts.SentientArticulationEngine(viewModelScope)

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentSentenceIndex }
                .distinctUntilChanged()
                .collect { sentenceIdx ->
                    val doc = _parsedDocument.value
                    if (doc != null) {
                        sentientArticulationEngine.processLookahead(doc.sentences, sentenceIdx)
                    }
                }
        }
    }

    private val _dynamicFormatMode = MutableStateFlow(com.example.tts.DynamicFormatMode.DEEP_DIVE)
    val dynamicFormatMode: StateFlow<com.example.tts.DynamicFormatMode> = _dynamicFormatMode.asStateFlow()

    private val _previouslyOnRecapText = MutableStateFlow<String?>(null)
    val previouslyOnRecapText: StateFlow<String?> = _previouslyOnRecapText.asStateFlow()

    private val _isRecapLoading = MutableStateFlow(false)
    val isRecapLoading: StateFlow<Boolean> = _isRecapLoading.asStateFlow()

    fun setDynamicFormatMode(mode: com.example.tts.DynamicFormatMode) {
        _dynamicFormatMode.value = mode
    }

    fun generatePreviouslyOnRecap() {
        val doc = _parsedDocument.value ?: return
        val currentSentenceIndex = playbackState.value.currentSentenceIndex
        if (currentSentenceIndex <= 0) return

        _isRecapLoading.value = true
        _previouslyOnRecapText.value = null

        viewModelScope.launch {
            val pastSnippet = doc.sentences.take(currentSentenceIndex).takeLast(10).joinToString(" ") { it.text }
            val result = com.example.ai.GeminiTextPreprocessor.askSocraticCoPilot(
                userQuery = "Give me a dramatic 60-second 'Previously On...' recap of what happened in the book leading up to this point.",
                currentSentenceContext = pastSnippet,
                documentTitle = doc.title
            )
            _isRecapLoading.value = false
            val recap = result.getOrElse { "Previously on ${doc.title}: You were at sentence ${currentSentenceIndex + 1}." }
            _previouslyOnRecapText.value = recap
            speechEngine.speakRawText("Previously on ${doc.title}. $recap")
        }
    }

    private val _isBionicReadingEnabled = MutableStateFlow(true)
    val isBionicReadingEnabled: StateFlow<Boolean> = _isBionicReadingEnabled.asStateFlow()

    private val _socraticAnswer = MutableStateFlow<String?>(null)
    val socraticAnswer: StateFlow<String?> = _socraticAnswer.asStateFlow()

    private val _isSocraticLoading = MutableStateFlow(false)
    val isSocraticLoading: StateFlow<Boolean> = _isSocraticLoading.asStateFlow()

    private val _quizText = MutableStateFlow<String?>(null)
    val quizText: StateFlow<String?> = _quizText.asStateFlow()

    private val _isQuizLoading = MutableStateFlow(false)
    val isQuizLoading: StateFlow<Boolean> = _isQuizLoading.asStateFlow()

    fun toggleBionicReading() {
        _isBionicReadingEnabled.value = !_isBionicReadingEnabled.value
    }

    fun askSocraticCompanion(query: String) {
        val doc = _parsedDocument.value ?: return
        val currentSentenceIndex = playbackState.value.currentSentenceIndex
        val currentSentence = doc.sentences.getOrNull(currentSentenceIndex)?.text ?: ""

        _isSocraticLoading.value = true
        _socraticAnswer.value = null

        viewModelScope.launch {
            val result = com.example.ai.GeminiTextPreprocessor.askSocraticCoPilot(
                userQuery = query,
                currentSentenceContext = currentSentence,
                documentTitle = doc.title
            )
            _isSocraticLoading.value = false
            _socraticAnswer.value = result.getOrElse { "Socratic AI Co-Pilot Error: ${it.localizedMessage}" }
        }
    }

    fun generateQuizForCurrentSection() {
        val doc = _parsedDocument.value ?: return
        val currentSentenceIndex = playbackState.value.currentSentenceIndex
        val startIdx = (currentSentenceIndex - 2).coerceAtLeast(0)
        val endIdx = (currentSentenceIndex + 8).coerceAtMost(doc.sentences.size - 1)
        val snippet = doc.sentences.subList(startIdx, (endIdx + 1).coerceAtMost(doc.sentences.size)).joinToString(" ") { it.text }

        _isQuizLoading.value = true
        _quizText.value = null

        viewModelScope.launch {
            val result = com.example.ai.GeminiTextPreprocessor.generateActiveRecallQuiz(
                documentSnippet = snippet,
                title = doc.title
            )
            _isQuizLoading.value = false
            _quizText.value = result.getOrElse { "Active Recall Quiz Error: ${it.localizedMessage}" }
        }
    }

    fun generatePodcastScript() {
        val doc = _parsedDocument.value ?: return
        val currentSentenceIndex = playbackState.value.currentSentenceIndex
        val startIdx = (currentSentenceIndex - 4).coerceAtLeast(0)
        val endIdx = (currentSentenceIndex + 12).coerceAtMost(doc.sentences.size - 1)
        val snippet = doc.sentences.subList(startIdx, (endIdx + 1).coerceAtMost(doc.sentences.size)).joinToString(" ") { it.text }

        _isSocraticLoading.value = true
        _socraticAnswer.value = null

        viewModelScope.launch {
            val result = com.example.ai.GeminiTextPreprocessor.generatePodcastDialogueScript(
                documentSnippet = snippet,
                title = doc.title
            )
            _isSocraticLoading.value = false
            _socraticAnswer.value = result.getOrElse { "Podcast Script Error: ${it.localizedMessage}" }
        }
    }

    fun setLyricTheme(preset: LyricThemePreset) {
        _selectedLyricTheme.value = preset
    }

    fun setFontSizeSp(sp: Int) {
        _fontSizeSp.value = sp.coerceIn(16, 42)
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesRemaining.value = minutes

        sleepTimerJob = viewModelScope.launch(Dispatchers.Default) {
            var remainingSeconds = minutes * 60
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                _sleepTimerMinutesRemaining.value = (remainingSeconds / 60) + 1
            }
            speechEngine.pause()
            _sleepTimerMinutesRemaining.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesRemaining.value = null
    }

    override fun onCleared() {
        super.onCleared()
        saveProgressToDb()
        speechEngine.shutdown()
    }
}
