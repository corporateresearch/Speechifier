package com.speechifier.assets

import android.content.Context
import com.speechifier.tts.G2pTier
import com.speechifier.tts.Voice
import java.io.File

/** A single downloadable asset and where it lives on device. */
data class AssetEntry(
    val key: String,
    val url: String,
    val dest: File,
    val approxBytes: Long,
    val label: String,
) {
    val present: Boolean get() = dest.exists() && dest.length() > 0
}

/**
 * On-device locations for the model, vocab, voice style packs and G2P data.
 * Everything lives under `filesDir/assets/` and is downloaded on demand (the APK
 * stays small). See the plan's asset-delivery section.
 */
class AssetStore(context: Context) {
    val root = File(context.filesDir, "assets")

    val model = File(root, "kokoro_int8_timestamped.onnx")
    val vocab = File(root, "vocab.json")
    val voicesDir = File(root, "voices")
    val espeakDataDir = File(root, "espeak-ng-data")
    val japaneseDir = File(root, "openjtalk")
    val chineseDir = File(root, "zh")

    fun voiceFile(voiceId: String) = File(voicesDir, "$voiceId.bin")
}

/**
 * Describes what must be present to synthesize a given voice: the shared model +
 * vocab, that voice's style pack, and its G2P tier's data. [missingFor] returns
 * only the entries not yet on disk, for a download prompt.
 *
 * Set [baseUrl] to wherever you host the release assets.
 */
object AssetManifest {

    /** Configure for your release host (e.g. a GitHub release or CDN). */
    var baseUrl: String = "https://example.invalid/speechifier-assets"

    private fun core(store: AssetStore) = listOf(
        AssetEntry("model", "$baseUrl/kokoro_int8_timestamped.onnx", store.model, 80_000_000, "TTS model"),
        AssetEntry("vocab", "$baseUrl/vocab.json", store.vocab, 20_000, "Tokenizer"),
    )

    private fun voiceEntry(store: AssetStore, voice: Voice) = AssetEntry(
        key = "voice:${voice.id}",
        url = "$baseUrl/voices/${voice.id}.bin",
        dest = store.voiceFile(voice.id),
        approxBytes = 520_000,
        label = "Voice: ${voice.label}",
    )

    // G2P data bundles ship as zips; a `.ready` marker signals successful unpack.
    // TODO: unzip-on-complete in the download flow (Downloader fetches the zip).
    private fun tierEntry(store: AssetStore, tier: G2pTier): AssetEntry? = when (tier) {
        G2pTier.ESPEAK -> AssetEntry(
            "espeak-data", "$baseUrl/espeak-ng-data.zip",
            File(store.espeakDataDir, ".ready"), 12_000_000, "Pronunciation data (espeak-ng)",
        )
        G2pTier.JAPANESE -> AssetEntry(
            "unidic", "$baseUrl/openjtalk-unidic.zip",
            File(store.japaneseDir, ".ready"), 60_000_000, "Japanese dictionary (UniDic)",
        )
        G2pTier.CHINESE -> AssetEntry(
            "zh-data", "$baseUrl/zh-data.zip",
            File(store.chineseDir, ".ready"), 40_000_000, "Chinese dictionary (jieba + pinyin)",
        )
    }

    /** All assets required to read with [voice]. */
    fun requirementsFor(store: AssetStore, voice: Voice): List<AssetEntry> =
        buildList {
            addAll(core(store))
            add(voiceEntry(store, voice))
            tierEntry(store, voice.tier)?.let { add(it) }
        }

    /** Subset of [requirementsFor] not yet on disk. */
    fun missingFor(store: AssetStore, voice: Voice): List<AssetEntry> =
        requirementsFor(store, voice).filterNot { it.present }
}
