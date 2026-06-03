package com.speechifier.tts

/**
 * Port of the per-word timing logic in the server's `backend/tts.py`
 * (`_group_words` + `_fill_missing_timings`).
 *
 * Kokoro emits one timing per *token*; the UI highlights *display words*. Tokens
 * whose trailing whitespace is empty are glued onto the following token (this is
 * how punctuation and quotes attach, e.g. "dog" + "." -> "dog."). A display
 * word's span is [min(token starts), max(token ends)].
 *
 * This is language-agnostic: every [Phonemizer]/engine produces the same [Token]
 * stream, so it is validated once.
 */
object WordTimer {

    /**
     * A model token with its alignment span (seconds), as recovered from the
     * timestamped ONNX model's duration output.
     *
     * @param text the token's surface text (may be punctuation or empty)
     * @param hasTrailingWhitespace whether a space follows this token; when false
     *   the token is glued to the next (mirrors Kokoro's `whitespace` metadata)
     * @param start token onset in seconds, or null if unknown
     * @param end token offset in seconds, or null if unknown
     */
    data class Token(
        val text: String,
        val hasTrailingWhitespace: Boolean,
        val start: Double?,
        val end: Double?,
    )

    /** A display word with a resolved [start]/[end] in seconds and its index [i]. */
    data class Word(
        val i: Int,
        val text: String,
        val start: Double,
        val end: Double,
    )

    /**
     * Group [tokens] into display words, offsetting all times by [timeOffset]
     * seconds (Kokoro may yield more than one chunk; later chunks start later).
     * Returns words whose start/end may still be null — resolve with [fillMissing].
     */
    private data class RawWord(val text: String, var start: Double?, var end: Double?)

    private fun groupWords(tokens: List<Token>, timeOffset: Double): List<RawWord> {
        val words = mutableListOf<RawWord>()
        val group = mutableListOf<Token>()

        fun flush() {
            if (group.isEmpty()) return
            val text = group.joinToString("") { it.text }
            if (text.isEmpty()) {
                group.clear()
                return
            }
            val starts = group.mapNotNull { it.start }
            val ends = group.mapNotNull { it.end }
            val start = if (starts.isNotEmpty()) starts.min() + timeOffset else null
            val end = if (ends.isNotEmpty()) ends.max() + timeOffset else null
            words.add(RawWord(text, start, end))
            group.clear()
        }

        for (t in tokens) {
            group.add(t)
            if (t.hasTrailingWhitespace) flush()
        }
        flush()
        return words
    }

    /** Ensure every word has a usable [start, end] by interpolating gaps from neighbours. */
    private fun fillMissing(words: List<RawWord>, total: Double) {
        val n = words.size
        for (i in 0 until n) {
            val w = words[i]
            if (w.start == null) {
                val prevEnd = if (i > 0) words[i - 1].end else null
                w.start = prevEnd ?: 0.0
            }
            if (w.end == null) {
                val nxt = if (i + 1 < n) words[i + 1].start else null
                w.end = maxOf(w.start!!, nxt ?: total)
            }
        }
    }

    /**
     * Convert a token stream + total audio [duration] (seconds) into indexed
     * display [Word]s. Pass [timeOffset] when concatenating multiple chunks.
     */
    fun toWords(tokens: List<Token>, duration: Double, timeOffset: Double = 0.0): List<Word> {
        val raw = groupWords(tokens, timeOffset)
        fillMissing(raw, duration)
        return raw.mapIndexed { i, w -> Word(i = i, text = w.text, start = w.start!!, end = w.end!!) }
    }

    /**
     * Index of the word active at playback position [t] (seconds), or -1 if none.
     * Replaces the web app's rAF lookup; called from the AudioTrack position loop.
     */
    fun activeWordIndex(words: List<Word>, t: Double): Int {
        if (words.isEmpty()) return -1
        // Words are ordered; a small linear scan is fine for one sentence.
        for (w in words) {
            if (t < w.end) return if (t >= w.start) w.i else maxOf(0, w.i - 1).coerceAtMost(w.i)
        }
        return words.last().i
    }
}
