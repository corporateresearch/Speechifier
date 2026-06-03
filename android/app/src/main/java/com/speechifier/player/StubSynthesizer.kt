package com.speechifier.player

import com.speechifier.text.toDisplayWords
import com.speechifier.tts.WordTimer
import kotlinx.coroutines.delay

/**
 * Interim [Synthesizer] used until [com.speechifier.tts.KokoroEngine] (task #6)
 * lands. It produces **silent** PCM of a plausible duration and evenly-spaced
 * word timings derived from the sentence's display words, so the reader UI —
 * highlighting, auto-scroll, controls, prefetch — is fully exercisable without
 * the model. Swap this for KokoroEngine via the same [Synthesizer] interface.
 */
class StubSynthesizer(override val sampleRate: Int = 24_000) : Synthesizer {

    override suspend fun synthesize(text: String, voiceId: String, speed: Float): SynthesisResult {
        // Pretend synthesis takes a little time so prefetch/caching behaviour is visible.
        delay(120)

        val displayWords = text.toDisplayWords()
        val perWord = SECONDS_PER_WORD / speed.coerceIn(0.5f, 2.0f)
        val duration = (displayWords.size * perWord).coerceAtLeast(0.2)

        val words = displayWords.mapIndexed { i, w ->
            WordTimer.Word(i = i, text = w, start = i * perWord, end = (i + 1) * perWord)
        }
        val pcm = FloatArray((duration * sampleRate).toInt())
        return SynthesisResult(pcm = pcm, sampleRate = sampleRate, durationSec = duration, words = words)
    }

    private companion object {
        const val SECONDS_PER_WORD = 0.36
    }
}
