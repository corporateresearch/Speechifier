package com.speechifier.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class WordTimerTest {

    private fun tok(text: String, ws: Boolean, start: Double?, end: Double?) =
        WordTimer.Token(text, ws, start, end)

    @Test
    fun gluesPunctuationOntoPrecedingWord() {
        // "dog" (no trailing space) + "." (trailing space) -> "dog."
        val tokens = listOf(
            tok("The", true, 0.0, 0.2),
            tok("dog", false, 0.2, 0.5),
            tok(".", true, 0.5, 0.6),
        )
        val words = WordTimer.toWords(tokens, duration = 0.6)
        assertEquals(listOf("The", "dog."), words.map { it.text })
        assertEquals(0.2, words[1].start, 1e-9) // min of group starts
        assertEquals(0.6, words[1].end, 1e-9)   // max of group ends
    }

    @Test
    fun appliesTimeOffsetForLaterChunks() {
        val tokens = listOf(tok("Hi", true, 0.0, 0.3))
        val words = WordTimer.toWords(tokens, duration = 1.3, timeOffset = 1.0)
        assertEquals(1.0, words[0].start, 1e-9)
        assertEquals(1.3, words[0].end, 1e-9)
    }

    @Test
    fun interpolatesMissingTimings() {
        val tokens = listOf(
            tok("a", true, 0.0, 0.5),
            tok("b", true, null, null), // unknown -> fill from neighbours
            tok("c", true, 1.0, 1.5),
        )
        val words = WordTimer.toWords(tokens, duration = 1.5)
        assertEquals(0.5, words[1].start, 1e-9) // prev end
        assertEquals(1.0, words[1].end, 1e-9)   // next start
    }

    @Test
    fun activeWordIndexTracksPlayback() {
        val tokens = listOf(
            tok("one", true, 0.0, 0.5),
            tok("two", true, 0.5, 1.0),
            tok("three", true, 1.0, 1.5),
        )
        val words = WordTimer.toWords(tokens, duration = 1.5)
        assertEquals(0, WordTimer.activeWordIndex(words, 0.1))
        assertEquals(1, WordTimer.activeWordIndex(words, 0.7))
        assertEquals(2, WordTimer.activeWordIndex(words, 1.4))
        assertEquals(2, WordTimer.activeWordIndex(words, 9.9)) // past end clamps to last
    }
}
