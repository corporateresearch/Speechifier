package com.speechifier.player

/**
 * Small LRU cache of synthesized sentences, keyed by (sentence index, voice,
 * speed) — mirroring the server's per-(doc, sentence, voice, speed) cache in
 * `tts.py`. Makes replays and prev/next instant.
 */
class SentenceCache(private val maxEntries: Int = 24) {

    private data class Key(val index: Int, val voiceId: String, val speedMilli: Int)

    // accessOrder = true makes this a true LRU.
    private val map = object : LinkedHashMap<Key, SynthesisResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, SynthesisResult>) =
            size > maxEntries
    }

    private fun key(index: Int, voiceId: String, speed: Float) =
        Key(index, voiceId, Math.round(speed * 1000)) // round speed to 3 decimals like tts.py

    @Synchronized
    fun get(index: Int, voiceId: String, speed: Float): SynthesisResult? =
        map[key(index, voiceId, speed)]

    @Synchronized
    fun put(index: Int, voiceId: String, speed: Float, result: SynthesisResult) {
        map[key(index, voiceId, speed)] = result
    }

    @Synchronized
    fun clear() = map.clear()
}
