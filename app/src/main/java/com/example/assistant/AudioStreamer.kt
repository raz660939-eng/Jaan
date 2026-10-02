package com.example.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

/**
 * Handles raw low-latency audio capture (PCM 16-bit, 16 kHz Mono)
 * and audio playback (PCM 16-bit, 24 kHz Mono) for the Gemini Live API.
 */
class AudioStreamer(private val context: Context) {

    companion object {
        private const val TAG = "AudioStreamer"
        const val INPUT_SAMPLE_RATE = 16000
        const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHUNK_SIZE = 1024 // 1024 samples = 2048 bytes
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val audioPlaybackQueue = ConcurrentLinkedQueue<ByteArray>()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    var onAudioChunkCaptured: ((ByteArray) -> Unit)? = null
    var onPlaybackFinished: (() -> Unit)? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(8192)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(OUTPUT_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            startPlaybackWorker()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    private fun startPlaybackWorker() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive) {
                val chunk = audioPlaybackQueue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    _isPlaying.value = true
                    val amplitude = computeRms(chunk)
                    _outputAmplitude.value = amplitude

                    audioTrack?.let { track ->
                        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            track.play()
                        }
                        track.write(chunk, 0, chunk.size)
                    }
                } else {
                    if (_isPlaying.value && audioPlaybackQueue.isEmpty()) {
                        _isPlaying.value = false
                        _outputAmplitude.value = 0f
                        onPlaybackFinished?.invoke()
                    }
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (_isRecording.value) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch {
                val buffer = ByteArray(CHUNK_SIZE * 2) // 16-bit samples = 2 bytes each
                while (isActive && _isRecording.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        val chunk = buffer.copyOf(bytesRead)
                        val amp = computeRms(chunk)
                        _inputAmplitude.value = amp
                        onAudioChunkCaptured?.invoke(chunk)
                    } else {
                        kotlinx.coroutines.delay(10)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord", e)
            _isRecording.value = false
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            _inputAmplitude.value = 0f
        }
    }

    /**
     * Enqueue a PCM16 24kHz audio chunk for low-latency playback.
     */
    fun enqueueAudioChunk(pcmData: ByteArray) {
        audioPlaybackQueue.offer(pcmData)
    }

    /**
     * Immediately interrupts playback, clears remaining audio queue,
     * flushes the AudioTrack, and restores playback state ready for next sound.
     */
    fun interrupt() {
        audioPlaybackQueue.clear()
        _isPlaying.value = false
        _outputAmplitude.value = 0f
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack on interrupt", e)
        }
    }

    fun release() {
        stopRecording()
        interrupt()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    /**
     * Computes the normalized Root Mean Square (RMS) volume [0.0..1.0]
     */
    private fun computeRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.isEmpty()) return 0f
        val shorts = ShortArray(pcmBytes.size / 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        var sumSquares = 0.0
        for (sample in shorts) {
            sumSquares += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSquares / shorts.size)
        // Normalize against max short value ~ 32767 with dynamic boost for UI clarity
        val normalized = (rms / 32768.0).toFloat() * 2.8f
        return normalized.coerceIn(0f, 1f)
    }
}
