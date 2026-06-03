package com.speechifier.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real [TtsPlayer] backed by [AudioTrack] (24 kHz mono float PCM, streamed).
 *
 * Word highlighting reads playback position from `playbackHeadPosition` rather
 * than a wall clock, so the highlight stays sample-accurate even if audio
 * underruns — the on-device replacement for the web app's `<audio>` + rAF loop.
 *
 * A single coroutine both streams PCM (non-blocking writes, so pause/seek are
 * responsive) and emits position at ~60 Hz. `flush()` resets the head position,
 * so absolute position = segment base frame + head position.
 *
 * @param scope coroutine scope tied to the owner's (ViewModel's) lifecycle.
 */
class AudioTrackPlayer(private val scope: CoroutineScope) : TtsPlayer {

    private val _position = MutableStateFlow(0.0)
    override val positionSec: StateFlow<Double> = _position.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var pcm: FloatArray = FloatArray(0)
    private var sampleRate: Int = 24_000
    private var totalFrames: Int = 0

    /** Absolute frame the next play segment should start from. */
    private var cursorFrame: Int = 0

    private var track: AudioTrack? = null
    private var segmentJob: Job? = null
    private var onComplete: (() -> Unit)? = null

    override fun load(pcm: FloatArray, sampleRate: Int, durationSec: Double) {
        stopInternal(resetCursor = true)
        this.pcm = pcm
        this.sampleRate = sampleRate
        this.totalFrames = pcm.size // mono: 1 sample == 1 frame
        _position.value = 0.0
        _isPlaying.value = false
    }

    override fun play() {
        if (_isPlaying.value || totalFrames == 0) return
        _isPlaying.value = true
        val from = cursorFrame.coerceIn(0, totalFrames)
        segmentJob = scope.launch { runSegment(from) }
    }

    override fun pause() {
        if (!_isPlaying.value) return
        val head = track?.playbackHeadPosition ?: 0
        cursorFrame = (segmentBase + head).coerceIn(0, totalFrames)
        segmentJob?.cancel()
        segmentJob = null
        track?.pause()
        _isPlaying.value = false
    }

    override fun stop() {
        stopInternal(resetCursor = true)
        _position.value = 0.0
        _isPlaying.value = false
    }

    override fun seekTo(sec: Double) {
        val frame = (sec * sampleRate).toInt().coerceIn(0, totalFrames)
        cursorFrame = frame
        _position.value = frame.toDouble() / sampleRate
        if (_isPlaying.value) {
            segmentJob?.cancel()
            segmentJob = scope.launch { runSegment(frame) }
        }
    }

    override fun setOnComplete(callback: () -> Unit) {
        onComplete = callback
    }

    override fun release() {
        stopInternal(resetCursor = true)
        track?.release()
        track = null
        onComplete = null
    }

    // ---- internals ---------------------------------------------------------

    private var segmentBase: Int = 0

    private suspend fun runSegment(fromFrame: Int) {
        val t = ensureTrack()
        // Reset the head position so it counts frames from this segment's start.
        t.pause()
        t.flush()
        segmentBase = fromFrame
        _position.value = fromFrame.toDouble() / sampleRate
        t.play()

        var offset = fromFrame
        while (scope.isActive) {
            val head = t.playbackHeadPosition
            val current = segmentBase + head
            _position.value = current.toDouble() / sampleRate

            if (offset < totalFrames) {
                val toWrite = minOf(CHUNK_FRAMES, totalFrames - offset)
                val written = t.write(pcm, offset, toWrite, AudioTrack.WRITE_NON_BLOCKING)
                if (written > 0) offset += written
                // written == 0 means the buffer is full; just wait and retry.
            } else if (current >= totalFrames) {
                // All data queued and drained.
                cursorFrame = totalFrames
                _position.value = totalFrames.toDouble() / sampleRate
                _isPlaying.value = false
                onComplete?.invoke()
                return
            }
            delay(FRAME_MS)
        }
    }

    private fun ensureTrack(): AudioTrack {
        val existing = track
        if (existing != null && existingMatchesFormat) return existing
        existing?.release()

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(CHUNK_FRAMES * Float.SIZE_BYTES)

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = newTrack
        trackSampleRate = sampleRate
        return newTrack
    }

    private var trackSampleRate: Int = 0
    private val existingMatchesFormat: Boolean
        get() = trackSampleRate == sampleRate

    private fun stopInternal(resetCursor: Boolean) {
        segmentJob?.cancel()
        segmentJob = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
        }
        if (resetCursor) cursorFrame = 0
    }

    private companion object {
        const val FRAME_MS = 16L // ~60 Hz position updates
        const val CHUNK_FRAMES = 2048
    }
}
