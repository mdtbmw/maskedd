package com.example.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogEventType(val label: String) {
    STATE_TRANSITION("State Transition"),
    ILLEGAL_TRANSITION("Illegal Transition Blocked"),
    TIMING_OFFSET("Timing Offset"),
    BUFFER_UNDERFLOW("Buffer Underflow"),
    AI_INFERENCE("AI Processing"),
    TTS_EVENT("TTS Speech Event"),
    AUDIO_SYNTH("Audio Synthesizer"),
    API_KEY_ROTATED("API Key Rotation"),
    TEXT_NORMALIZATION("Text Normalization")
}

data class DiagnosticLogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestampMs: Long = System.currentTimeMillis(),
    val timestampFormatted: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
    val eventType: LogEventType,
    val message: String,
    val timingOffsetMs: Long? = null,
    val bufferUnderflowCount: Int? = null,
    val stateFrom: String? = null,
    val stateTo: String? = null,
    val isError: Boolean = false
)

object TtsDiagnosticLogger {
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    private val _totalUnderflowEvents = MutableStateFlow(0)
    val totalUnderflowEvents: StateFlow<Int> = _totalUnderflowEvents.asStateFlow()

    private val _illegalTransitionCount = MutableStateFlow(0)
    val illegalTransitionCount: StateFlow<Int> = _illegalTransitionCount.asStateFlow()

    private val _averageTimingOffsetMs = MutableStateFlow(0L)
    val averageTimingOffsetMs: StateFlow<Long> = _averageTimingOffsetMs.asStateFlow()

    private var totalOffsetSum = 0L
    private var offsetCount = 0

    @Synchronized
    fun log(
        eventType: LogEventType,
        message: String,
        timingOffsetMs: Long? = null,
        bufferUnderflowCount: Int? = null,
        stateFrom: String? = null,
        stateTo: String? = null,
        isError: Boolean = false
    ) {
        val entry = DiagnosticLogEntry(
            eventType = eventType,
            message = message,
            timingOffsetMs = timingOffsetMs,
            bufferUnderflowCount = bufferUnderflowCount,
            stateFrom = stateFrom,
            stateTo = stateTo,
            isError = isError
        )

        if (eventType == LogEventType.ILLEGAL_TRANSITION || isError) {
            _illegalTransitionCount.value = _illegalTransitionCount.value + 1
        }
        if (eventType == LogEventType.BUFFER_UNDERFLOW || (bufferUnderflowCount ?: 0) > 0) {
            _totalUnderflowEvents.value = _totalUnderflowEvents.value + 1
        }
        if (timingOffsetMs != null) {
            totalOffsetSum += kotlin.math.abs(timingOffsetMs)
            offsetCount++
            _averageTimingOffsetMs.value = totalOffsetSum / offsetCount
        }

        val updated = (listOf(entry) + _logs.value).take(150)
        _logs.value = updated
    }

    fun clearLogs() {
        _logs.value = emptyList()
        _totalUnderflowEvents.value = 0
        _illegalTransitionCount.value = 0
        _averageTimingOffsetMs.value = 0L
        totalOffsetSum = 0L
        offsetCount = 0
    }
}
