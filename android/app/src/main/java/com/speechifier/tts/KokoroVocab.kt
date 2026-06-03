package com.speechifier.tts

import org.json.JSONObject
import java.io.InputStream

/**
 * Maps Kokoro phoneme symbols to token ids. Kokoro tokenizes per *symbol*
 * (single Unicode code point), padding the sequence with 0 at both ends.
 *
 * The id map ships with the model (e.g. the `vocab` field of the onnx-community
 * model's `config.json`/`tokenizer.json`); load it rather than hardcoding so it
 * always matches the deployed model.
 */
class KokoroVocab private constructor(private val symbolToId: Map<String, Int>) {

    /** Token id for the inter-word separator (space), used to split words. */
    val spaceId: Int? = symbolToId[" "]

    /**
     * Encode a phoneme string to token ids (no padding). Unknown symbols are
     * skipped. Iterates by code point so multi-byte IPA symbols map correctly.
     */
    fun encode(phonemes: String): IntArray {
        val ids = ArrayList<Int>(phonemes.length)
        var i = 0
        while (i < phonemes.length) {
            val cp = phonemes.codePointAt(i)
            val sym = String(Character.toChars(cp))
            symbolToId[sym]?.let { ids.add(it) }
            i += Character.charCount(cp)
        }
        return ids.toIntArray()
    }

    companion object {
        /** Load from a JSON object whose top level (or a `vocab` field) is symbol -> id. */
        fun fromJson(input: InputStream): KokoroVocab {
            val root = JSONObject(input.readBytes().decodeToString())
            val vocabObj = if (root.has("vocab")) root.getJSONObject("vocab") else root
            val map = HashMap<String, Int>(vocabObj.length())
            for (key in vocabObj.keys()) map[key] = vocabObj.getInt(key)
            return KokoroVocab(map)
        }
    }
}
