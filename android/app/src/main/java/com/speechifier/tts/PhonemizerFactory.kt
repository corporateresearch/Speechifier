package com.speechifier.tts

import com.speechifier.assets.AssetStore
import java.io.File

/**
 * Builds the per-tier [Phonemizer] map that [KokoroEngine] consumes, pointing
 * each engine at its on-device G2P data. Engines whose native libs/dictionaries
 * aren't present report `isReady() == false`; [KokoroEngine] degrades gracefully.
 */
object PhonemizerFactory {

    fun create(store: AssetStore): Map<G2pTier, Phonemizer> = mapOf(
        G2pTier.ESPEAK to EspeakPhonemizer(store.espeakDataDir.absolutePath),
        G2pTier.JAPANESE to JapanesePhonemizer(store.japaneseDir.absolutePath),
        G2pTier.CHINESE to ChinesePhonemizer(
            jiebaDir = store.chineseDir.absolutePath,
            pinyinLexicon = File(store.chineseDir, "pinyin_lexicon.txt").absolutePath,
        ),
    )
}
