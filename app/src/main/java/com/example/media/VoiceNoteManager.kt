package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min

data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val livePeaks: List<Int> = emptyList()
)

data class VoicePlaybackState(
    val playingMessageId: String? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class CompletedVoiceNote(
    val file: File,
    val durationMs: Long,
    val peaks: List<Int>
)

class VoiceNoteManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var recorder: MediaRecorder? = null
    private var activeRecordFile: File? = null
    private var recordStartMs: Long = 0L
    private var recordMeterJob: Job? = null
    private val capturedPeaks = mutableListOf<Int>()

    private var player: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    private val _recordingState = MutableStateFlow(VoiceRecordingState())
    val recordingState: StateFlow<VoiceRecordingState> = _recordingState.asStateFlow()

    private val _playbackState = MutableStateFlow(VoicePlaybackState())
    val playbackState: StateFlow<VoicePlaybackState> = _playbackState.asStateFlow()

    fun startRecording(targetFile: File): Result<Unit> {
        stopPlayback()
        cancelRecording()
        return runCatching {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(targetFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            activeRecordFile = targetFile
            recordStartMs = System.currentTimeMillis()
            capturedPeaks.clear()
            _recordingState.value = VoiceRecordingState(isRecording = true, elapsedMs = 0L, livePeaks = emptyList())

            recordMeterJob = scope.launch(Dispatchers.Main) {
                while (isActive && recorder != null) {
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = min(100, (amp * 100) / 24000).coerceAtLeast(10)
                    capturedPeaks.add(normalized)
                    _recordingState.value = VoiceRecordingState(
                        isRecording = true,
                        elapsedMs = System.currentTimeMillis() - recordStartMs,
                        livePeaks = capturedPeaks.takeLast(32)
                    )
                    delay(100)
                }
            }
        }.onFailure {
            cancelRecording()
        }
    }

    fun stopAndFinalizeRecording(): CompletedVoiceNote? {
        recordMeterJob?.cancel()
        recordMeterJob = null
        val file = activeRecordFile
        val elapsed = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(400L)
        val peaks = downsamplePeaks(capturedPeaks, 28)

        runCatching {
            recorder?.stop()
        }
        runCatching {
            recorder?.release()
        }
        recorder = null
        activeRecordFile = null
        _recordingState.value = VoiceRecordingState()

        return if (file != null && file.exists() && file.length() > 0L) {
            CompletedVoiceNote(file = file, durationMs = elapsed, peaks = peaks)
        } else {
            file?.delete()
            null
        }
    }

    fun cancelRecording() {
        recordMeterJob?.cancel()
        recordMeterJob = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        activeRecordFile?.delete()
        activeRecordFile = null
        capturedPeaks.clear()
        _recordingState.value = VoiceRecordingState()
    }

    fun togglePlayback(messageId: String, filePath: String) {
        val current = _playbackState.value
        if (current.playingMessageId == messageId && player != null) {
            if (current.isPlaying) {
                player?.pause()
                _playbackState.value = current.copy(isPlaying = false)
            } else {
                player?.start()
                _playbackState.value = current.copy(isPlaying = true)
                startProgressTicker(messageId)
            }
            return
        }

        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) return

        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            player = mp
            val dur = mp.duration.toLong().coerceAtLeast(1L)
            _playbackState.value = VoicePlaybackState(
                playingMessageId = messageId,
                isPlaying = true,
                progress = 0f,
                currentPositionMs = 0L,
                durationMs = dur
            )
            startProgressTicker(messageId)
        }
    }

    fun seekPlayback(messageId: String, fraction: Float) {
        val mp = player ?: return
        if (_playbackState.value.playingMessageId != messageId) return
        val clamped = fraction.coerceIn(0f, 1f)
        val targetMs = (mp.duration * clamped).toInt()
        runCatching { mp.seekTo(targetMs) }
        _playbackState.value = _playbackState.value.copy(
            progress = clamped,
            currentPositionMs = targetMs.toLong()
        )
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        _playbackState.value = VoicePlaybackState()
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }

    private fun startProgressTicker(messageId: String) {
        playbackProgressJob?.cancel()
        playbackProgressJob = scope.launch(Dispatchers.Main) {
            while (isActive && player != null && _playbackState.value.isPlaying) {
                val mp = player ?: break
                val dur = mp.duration.toLong().coerceAtLeast(1L)
                val pos = mp.currentPosition.toLong().coerceIn(0L, dur)
                _playbackState.value = VoicePlaybackState(
                    playingMessageId = messageId,
                    isPlaying = mp.isPlaying,
                    progress = pos.toFloat() / dur.toFloat(),
                    currentPositionMs = pos,
                    durationMs = dur
                )
                delay(80)
            }
        }
    }

    private fun downsamplePeaks(source: List<Int>, targetCount: Int): List<Int> {
        if (source.isEmpty()) return List(targetCount) { 24 + (it * 13) % 55 }
        if (source.size <= targetCount) {
            return source + List(targetCount - source.size) { 18 }
        }
        val bucket = source.size.toFloat() / targetCount.toFloat()
        return List(targetCount) { idx ->
            val start = (idx * bucket).toInt().coerceIn(0, source.lastIndex)
            val end = ((idx + 1) * bucket).toInt().coerceIn(start + 1, source.size)
            source.subList(start, end).maxOrNull() ?: 20
        }
    }
}
