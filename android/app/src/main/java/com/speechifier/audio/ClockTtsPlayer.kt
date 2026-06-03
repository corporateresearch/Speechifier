package com.speechifier.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Interim [TtsPlayer] that advances a clock instead of producing sound, so the
 * reader's highlight/auto-scroll/sequencing logic is exercisable before
 * [AudioTrackPlayer] (task #6) exists. Emits position at ~60 Hz.
 *
 * @param scope a coroutine scope tied to the ViewModel's lifecycle.
 */
class ClockTtsPlayer(private val scope: CoroutineScope) : TtsPlayer {

    private val _position = MutableStateFlow(0.0)
    override val positionSec: StateFlow<Double> = _position.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var duration = 0.0
    private var onComplete: (() -> Unit)? = null
    private var ticker: Job? = null

    override fun load(pcm: FloatArray, sampleRate: Int, durationSec: Double) {
        stopTicker()
        duration = durationSec
        _position.value = 0.0
        _isPlaying.value = false
    }

    override fun play() {
        if (_isPlaying.value || duration <= 0.0) return
        _isPlaying.value = true
        ticker = scope.launch {
            var last = System.nanoTime()
            while (isActive && _isPlaying.value) {
                delay(FRAME_MS)
                val now = System.nanoTime()
                val dt = (now - last) / 1_000_000_000.0
                last = now
                val next = _position.value + dt
                if (next >= duration) {
                    _position.value = duration
                    _isPlaying.value = false
                    onComplete?.invoke()
                    break
                }
                _position.value = next
            }
        }
    }

    override fun pause() {
        _isPlaying.value = false
        stopTicker()
    }

    override fun stop() {
        pause()
        _position.value = 0.0
        duration = 0.0
    }

    override fun seekTo(sec: Double) {
        _position.value = sec.coerceIn(0.0, if (duration > 0) duration else sec)
    }

    override fun setOnComplete(callback: () -> Unit) {
        onComplete = callback
    }

    override fun release() {
        stopTicker()
        onComplete = null
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val FRAME_MS = 16L // ~60 Hz
    }
}
