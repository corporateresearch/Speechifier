package com.speechifier.tts

import android.util.Log

/**
 * Tier-B G2P for Japanese (`j`) via OpenJTalk + UniDic (the on-device equivalent
 * of misaki[ja]'s pyopenjtalk path). OpenJTalk produces full-context labels with
 * pitch accent; the native side converts those to Kokoro's Japanese phoneme set.
 *
 * The native engine lives in `src/main/cpp/japanese_g2p.cpp` and is built into
 * `libspeechify_g2p.so`. Until OpenJTalk + the UniDic dictionary are wired into
 * the NDK build, [nativeInit] returns false and this reports not-ready.
 *
 * @param dictDir absolute path to the extracted UniDic/OpenJTalk dictionary.
 */
class JapanesePhonemizer(private val dictDir: String) : Phonemizer {

    override val tier: G2pTier = G2pTier.JAPANESE

    private val ready: Boolean = try {
        System.loadLibrary("speechify_g2p")
        nativeInit(dictDir)
    } catch (t: Throwable) {
        Log.w(TAG, "OpenJTalk init failed: ${t.message}")
        false
    }

    override fun isReady(): Boolean = ready

    override fun phonemize(text: String, langCode: Char): String {
        if (!ready || text.isBlank()) return ""
        return nativePhonemize(text)
    }

    private external fun nativeInit(dictDir: String): Boolean
    private external fun nativePhonemize(text: String): String

    private companion object {
        const val TAG = "JapanesePhonemizer"
    }
}
