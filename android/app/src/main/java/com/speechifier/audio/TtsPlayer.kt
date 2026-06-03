package com.speechifier.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Plays a single sentence's PCM and exposes a playback position the UI polls to
 * drive word highlighting (replacing the web app's `<audio>` + rAF loop).
 *
 * The real implementation ([AudioTrackPlayer], task #6) streams to `AudioTrack`
 * and reads `getPlaybackHeadPosition()`; [ClockTtsPlayer] simulates a clock so the
 * reader works before audio is wired.
 */
interface TtsPlayer {
    /** Current playback position in seconds for the loaded clip. */
    val positionSec: StateFlow<Double>

    /** Whether audio is actively advancing. */
    val isPlaying: StateFlow<Boolean>

    /** Load a clip (does not start playback). Resets position to 0. */
    fun load(pcm: FloatArray, sampleRate: Int, durationSec: Double)

    fun play()
    fun pause()

    /** Stop and clear the loaded clip. */
    fun stop()

    /** Seek within the loaded clip. */
    fun seekTo(sec: Double)

    /** Invoked when the loaded clip finishes. */
    fun setOnComplete(callback: () -> Unit)

    fun release()
}
