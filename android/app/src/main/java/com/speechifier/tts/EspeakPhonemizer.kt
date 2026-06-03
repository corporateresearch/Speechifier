package com.speechifier.tts

import android.util.Log

/**
 * Tier-A G2P via espeak-ng (NDK/JNI). Covers US/UK English, Spanish, French,
 * Hindi, Italian and Brazilian Portuguese.
 *
 * The native side lives in `src/main/cpp/native_g2p.cpp`. Milestone 2 fills in
 * the espeak-ng build + data; until then [nativeInit] returns false and this
 * reports not-ready so callers can degrade gracefully.
 *
 * ⚠️ espeak-ng is GPL — see the plan's licensing note before distribution.
 *
 * @param dataPath absolute path to the extracted `espeak-ng-data` directory.
 */
class EspeakPhonemizer(private val dataPath: String) : Phonemizer {

    override val tier: G2pTier = G2pTier.ESPEAK

    private val ready: Boolean = try {
        System.loadLibrary("speechify_g2p")
        nativeInit(dataPath)
    } catch (t: Throwable) {
        Log.w(TAG, "espeak-ng init failed: ${t.message}")
        false
    }

    override fun isReady(): Boolean = ready

    override fun phonemize(text: String, langCode: Char): String {
        if (!ready || text.isBlank()) return ""
        return nativePhonemize(text, langCode.toString())
    }

    private external fun nativeInit(dataPath: String): Boolean
    private external fun nativePhonemize(text: String, langCode: String): String

    private companion object {
        const val TAG = "EspeakPhonemizer"
    }
}
