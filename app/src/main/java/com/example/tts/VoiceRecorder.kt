package com.example.tts

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    fun startRecording(scope: kotlinx.coroutines.CoroutineScope): File? {
        try {
            stopRecording()

            val dir = File(context.cacheDir, "voice_clones").apply { mkdirs() }
            val file = File(dir, "clone_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _isRecording.value = true
            _recordingSeconds.value = 0

            timerJob?.cancel()
            timerJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                while (_isRecording.value && _recordingSeconds.value < 30) {
                    kotlinx.coroutines.delay(1000L)
                    _recordingSeconds.value += 1
                }
                if (_recordingSeconds.value >= 30) {
                    stopRecording()
                }
            }

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return null
        }
    }

    fun stopRecording(): File? {
        timerJob?.cancel()
        timerJob = null
        try {
            if (_isRecording.value) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            }
        } catch (_: Exception) {
            mediaRecorder?.release()
        } finally {
            mediaRecorder = null
            _isRecording.value = false
        }
        return outputFile
    }
}
