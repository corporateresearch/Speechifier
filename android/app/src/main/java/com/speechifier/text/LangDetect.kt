package com.speechifier.text

import com.speechifier.tts.G2pTier
import com.speechifier.tts.Voice

/**
 * Routes a chosen voice to its language/G2P tier.
 *
 * The app does not auto-detect document language; the user picks a voice, and the
 * voice's [Voice.langCode]/[Voice.tier] determine phonemization (mirroring how the
 * server keys synthesis off the selected voice in `tts.py`).
 */
object LangDetect {

    /** The G2P engine tier required for [voice]. */
    fun tierFor(voice: Voice): G2pTier = voice.tier

    /** Kokoro language code for [voice], passed through to the phonemizer. */
    fun langCodeFor(voice: Voice): Char = voice.langCode

    /** Whether [voice] needs an on-demand dictionary download (Tier B/C). */
    fun requiresDownloadableAssets(voice: Voice): Boolean =
        voice.tier == G2pTier.JAPANESE || voice.tier == G2pTier.CHINESE
}
