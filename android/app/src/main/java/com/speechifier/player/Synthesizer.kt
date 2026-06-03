package com.speechifier.player

import com.speechifier.tts.WordTimer

/**
 * Result of synthesizing one sentence: raw mono PCM plus the per-word timings
 * used for highlighting. Mirrors the server's `/api/tts` response shape
 * (audio + words), but as in-memory floats instead of base64 WAV.
 */
data class SynthesisResult(
    val pcm: FloatArray,
    val sampleRate: Int,
    val durationSec: Double,
    val words: List<WordTimer.Word>,
) {
    // Arrays need explicit equals/hashCode; identity is fine for our cache use.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Synthesizes a sentence to audio + word timings. The real implementation
 * ([com.speechifier.tts.KokoroEngine], task #6) runs Kokoro via ONNX Runtime;
 * [StubSynthesizer] lets the UI run before the model is wired.
 */
interface Synthesizer {
    /** Kokoro sample rate (24 kHz mono), matching `tts.py`. */
    val sampleRate: Int

    suspend fun synthesize(text: String, voiceId: String, speed: Float): SynthesisResult
}
