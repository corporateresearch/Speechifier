package com.speechifier.tts

import android.util.Log

/**
 * Tier-C G2P for Mandarin (`z`) via cppjieba segmentation + a pinyin lexicon,
 * mirroring misaki[zh] (jieba + paddlespeech-style pinyin handling). The native
 * side segments the text, looks up per-syllable pinyin (with tone), and maps it
 * to Kokoro's Chinese phoneme set.
 *
 * The native engine lives in `src/main/cpp/chinese_g2p.cpp` (built into
 * `libspeechify_g2p.so`). Until cppjieba + the pinyin lexicon are wired into the
 * NDK build, [nativeInit] returns false and this reports not-ready.
 *
 * @param jiebaDir directory with jieba dictionaries.
 * @param pinyinLexicon path to the pinyin lexicon file.
 */
class ChinesePhonemizer(
    private val jiebaDir: String,
    private val pinyinLexicon: String,
) : Phonemizer {

    override val tier: G2pTier = G2pTier.CHINESE

    private val ready: Boolean = try {
        System.loadLibrary("speechify_g2p")
        nativeInit(jiebaDir, pinyinLexicon)
    } catch (t: Throwable) {
        Log.w(TAG, "jieba/pinyin init failed: ${t.message}")
        false
    }

    override fun isReady(): Boolean = ready

    override fun phonemize(text: String, langCode: Char): String {
        if (!ready || text.isBlank()) return ""
        return nativePhonemize(text)
    }

    private external fun nativeInit(jiebaDir: String, pinyinLexicon: String): Boolean
    private external fun nativePhonemize(text: String): String

    private companion object {
        const val TAG = "ChinesePhonemizer"
    }
}
