package com.speechifier.tts

/**
 * Which on-device G2P engine handles a given language (see the approved plan).
 *  - [ESPEAK]  Tier A: espeak-ng covers 7 languages.
 *  - [JAPANESE] Tier B: OpenJTalk + UniDic.
 *  - [CHINESE]  Tier C: cppjieba + pinyin lexicon.
 */
enum class G2pTier { ESPEAK, JAPANESE, CHINESE }

/**
 * A selectable Kokoro voice. [langCode] is Kokoro's single-letter language code
 * (also drives phonemization); [tier] selects the G2P engine.
 */
data class Voice(
    val id: String,
    val label: String,
    val language: String,
    val langCode: Char,
    val tier: G2pTier,
)

/**
 * Expanded multilingual port of `VOICES` in `backend/tts.py` — all 9 languages
 * Kokoro supports, not just the 6 English voices the server currently ships.
 *
 * Voice IDs follow Kokoro's `<lang><gender>_<name>` convention. Unknown ids fall
 * back to [DEFAULT_VOICE], matching the server.
 */
object Voices {

    const val DEFAULT_VOICE = "af_heart"

    val ALL: List<Voice> = buildList {
        // a — US English (Tier A)
        add(Voice("af_heart", "Heart (US female)", "US English", 'a', G2pTier.ESPEAK))
        add(Voice("af_bella", "Bella (US female)", "US English", 'a', G2pTier.ESPEAK))
        add(Voice("af_nicole", "Nicole (US female)", "US English", 'a', G2pTier.ESPEAK))
        add(Voice("af_sarah", "Sarah (US female)", "US English", 'a', G2pTier.ESPEAK))
        add(Voice("am_michael", "Michael (US male)", "US English", 'a', G2pTier.ESPEAK))
        add(Voice("am_fenrir", "Fenrir (US male)", "US English", 'a', G2pTier.ESPEAK))
        add(Voice("am_puck", "Puck (US male)", "US English", 'a', G2pTier.ESPEAK))

        // b — UK English (Tier A)
        add(Voice("bf_emma", "Emma (UK female)", "UK English", 'b', G2pTier.ESPEAK))
        add(Voice("bf_isabella", "Isabella (UK female)", "UK English", 'b', G2pTier.ESPEAK))
        add(Voice("bm_george", "George (UK male)", "UK English", 'b', G2pTier.ESPEAK))
        add(Voice("bm_lewis", "Lewis (UK male)", "UK English", 'b', G2pTier.ESPEAK))

        // e — Spanish (Tier A)
        add(Voice("ef_dora", "Dora (Spanish female)", "Spanish", 'e', G2pTier.ESPEAK))
        add(Voice("em_alex", "Alex (Spanish male)", "Spanish", 'e', G2pTier.ESPEAK))

        // f — French (Tier A)
        add(Voice("ff_siwis", "Siwis (French female)", "French", 'f', G2pTier.ESPEAK))

        // h — Hindi (Tier A)
        add(Voice("hf_alpha", "Alpha (Hindi female)", "Hindi", 'h', G2pTier.ESPEAK))
        add(Voice("hm_omega", "Omega (Hindi male)", "Hindi", 'h', G2pTier.ESPEAK))

        // i — Italian (Tier A)
        add(Voice("if_sara", "Sara (Italian female)", "Italian", 'i', G2pTier.ESPEAK))
        add(Voice("im_nicola", "Nicola (Italian male)", "Italian", 'i', G2pTier.ESPEAK))

        // p — Brazilian Portuguese (Tier A)
        add(Voice("pf_dora", "Dora (Portuguese female)", "Brazilian Portuguese", 'p', G2pTier.ESPEAK))
        add(Voice("pm_alex", "Alex (Portuguese male)", "Brazilian Portuguese", 'p', G2pTier.ESPEAK))

        // j — Japanese (Tier B)
        add(Voice("jf_alpha", "Alpha (Japanese female)", "Japanese", 'j', G2pTier.JAPANESE))
        add(Voice("jm_kumo", "Kumo (Japanese male)", "Japanese", 'j', G2pTier.JAPANESE))

        // z — Mandarin (Tier C)
        add(Voice("zf_xiaoxiao", "Xiaoxiao (Mandarin female)", "Mandarin", 'z', G2pTier.CHINESE))
        add(Voice("zm_yunxi", "Yunxi (Mandarin male)", "Mandarin", 'z', G2pTier.CHINESE))
    }

    private val byId: Map<String, Voice> = ALL.associateBy { it.id }

    /** Resolve a voice id, falling back to the default (matches server behaviour). */
    fun resolve(id: String?): Voice = byId[id] ?: byId.getValue(DEFAULT_VOICE)

    /** Voices grouped by language, in catalog order — for the language-grouped picker. */
    fun grouped(): Map<String, List<Voice>> = ALL.groupBy { it.language }
}
