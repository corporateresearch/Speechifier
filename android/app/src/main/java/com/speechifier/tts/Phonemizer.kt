package com.speechifier.tts

/**
 * Grapheme-to-phoneme conversion. Each language tier provides an implementation
 * (espeak-ng, OpenJTalk, cppjieba); [KokoroEngine] then maps the phoneme string
 * to Kokoro's token-id vocabulary before inference.
 *
 * See the approved plan's tiered-G2P section.
 */
interface Phonemizer {

    /** The tier this phonemizer serves. */
    val tier: G2pTier

    /** True once native libraries and any required dictionaries are loaded. */
    fun isReady(): Boolean

    /**
     * Convert [text] to a phoneme string in Kokoro's phoneme set, for the given
     * Kokoro [langCode]. Returns an empty string if [isReady] is false.
     */
    fun phonemize(text: String, langCode: Char): String
}
