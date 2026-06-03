package com.speechifier.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.speechifier.player.SynthesisResult
import com.speechifier.player.Synthesizer
import com.speechifier.text.toDisplayWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device Kokoro synthesizer (ONNX Runtime). Replaces the server's `KPipeline`
 * in `tts.py`.
 *
 * **Per-word timings without misaki metadata:** we phonemize each *display word*
 * separately and remember which token span each word owns. The timestamped model
 * returns a per-token duration; summing a word's token durations gives its
 * start/end. We convert frames→seconds via `durationSec / totalFrames`, so no
 * hop-length constant is needed. [WordTimer] then handles grouping/interpolation,
 * exactly as on the server.
 *
 * Items to VERIFY against the deployed `Kokoro-82M-v1.0-ONNX-timestamped` model
 * are marked inline (tensor names, duration dtype, style indexing, speed sense).
 *
 * @param modelPath path to the timestamped `.onnx`.
 * @param voicesDir directory of per-voice `.bin` style files (`<id>.bin`).
 * @param vocab phoneme→id map (loaded from the model's config).
 * @param phonemizers G2P engine per tier (espeak-ng / OpenJTalk / jieba).
 */
class KokoroEngine(
    private val modelPath: String,
    private val voicesDir: File,
    private val vocab: KokoroVocab,
    private val phonemizers: Map<G2pTier, Phonemizer>,
) : Synthesizer {

    override val sampleRate: Int = 24_000

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        }
        env.createSession(modelPath, opts)
    }
    private val runLock = Mutex() // OrtSession.run is not safe to call concurrently.
    private val voiceCache = HashMap<String, VoicePack>()

    override suspend fun synthesize(text: String, voiceId: String, speed: Float): SynthesisResult =
        withContext(Dispatchers.Default) {
            val voice = Voices.resolve(voiceId)
            val words = text.toDisplayWords()
            val phonemizer = phonemizers[voice.tier]

            // Build the padded token sequence and per-word token spans.
            val ids = ArrayList<Int>()
            ids.add(PAD)
            val spans = ArrayList<IntRange?>(words.size)
            for ((w, word) in words.withIndex()) {
                val phon = if (phonemizer?.isReady() == true) {
                    phonemizer.phonemize(word, voice.langCode)
                } else ""
                val wordIds = vocab.encode(phon)
                if (wordIds.isEmpty()) {
                    spans.add(null)
                } else {
                    val start = ids.size
                    wordIds.forEach { ids.add(it) }
                    spans.add(start until ids.size)
                }
                if (w < words.lastIndex) vocab.spaceId?.let { ids.add(it) }
            }
            ids.add(PAD)

            // No usable phonemes (e.g. G2P not ready) → silent clip with even timings.
            if (ids.size <= 2) return@withContext silentFallback(words, speed)

            val style = loadVoice(voiceId).styleFor(ids.size - 2) // VERIFY index basis
            val (pcm, durations) = runLock.withLock { runModel(ids.toIntArray(), style, speed) }

            val durationSec = pcm.size.toDouble() / sampleRate
            val words2 = buildWords(words, spans, durations, durationSec)
            SynthesisResult(pcm = pcm, sampleRate = sampleRate, durationSec = durationSec, words = words2)
        }

    /** Run inference: returns (waveform PCM, per-token frame durations). */
    private fun runModel(ids: IntArray, style: FloatArray, speed: Float): Pair<FloatArray, FloatArray> {
        val idsLong = arrayOf(LongArray(ids.size) { ids[it].toLong() }) // shape [1, L]
        val styleRow = arrayOf(style)                                   // shape [1, 256]
        val speedArr = floatArrayOf(speed)                              // shape [1]; VERIFY sense

        OnnxTensor.createTensor(env, idsLong).use { inputIds ->
            OnnxTensor.createTensor(env, styleRow).use { styleT ->
                OnnxTensor.createTensor(env, speedArr).use { speedT ->
                    val inputs = mapOf(IN_IDS to inputIds, IN_STYLE to styleT, IN_SPEED to speedT)
                    session.run(inputs).use { result ->
                        val pcm = flatFloats(result.get(OUT_WAVEFORM).get())
                        val durs = flatFloats(result.get(OUT_DURATIONS).get())
                        return pcm to durs
                    }
                }
            }
        }
    }

    /** Map each display word's token span to a [WordTimer.Token], then group/interpolate. */
    private fun buildWords(
        words: List<String>,
        spans: List<IntRange?>,
        durations: FloatArray,
        durationSec: Double,
    ): List<WordTimer.Word> {
        // Cumulative frames at each token boundary: cum[i] = sum(durations[0 until i]).
        val cum = DoubleArray(durations.size + 1)
        for (i in durations.indices) cum[i + 1] = cum[i] + durations[i]
        val totalFrames = cum.last()
        val secPerFrame = if (totalFrames > 0.0) durationSec / totalFrames else 0.0

        val tokens = words.mapIndexed { w, word ->
            val span = spans[w]
            if (span == null || span.isEmpty()) {
                WordTimer.Token(word, hasTrailingWhitespace = true, start = null, end = null)
            } else {
                val startFrame = cum.getOrElse(span.first) { 0.0 }
                val endFrame = cum.getOrElse(span.last + 1) { totalFrames }
                WordTimer.Token(word, true, startFrame * secPerFrame, endFrame * secPerFrame)
            }
        }
        return WordTimer.toWords(tokens, durationSec)
    }

    private fun loadVoice(voiceId: String): VoicePack =
        voiceCache.getOrPut(voiceId) { VoicePack.fromFile(File(voicesDir, "$voiceId.bin")) }

    /** Silent audio + evenly-spaced timings, used when phonemization is unavailable. */
    private fun silentFallback(words: List<String>, speed: Float): SynthesisResult {
        val perWord = 0.36 / speed.coerceIn(0.5f, 2.0f)
        val duration = (words.size * perWord).coerceAtLeast(0.2)
        val timed = words.mapIndexed { i, word ->
            WordTimer.Word(i, word, i * perWord, (i + 1) * perWord)
        }
        return SynthesisResult(FloatArray((duration * sampleRate).toInt()), sampleRate, duration, timed)
    }

    private companion object {
        const val PAD = 0

        // VERIFY these against the deployed model's named inputs/outputs.
        const val IN_IDS = "input_ids"
        const val IN_STYLE = "style"
        const val IN_SPEED = "speed"
        const val OUT_WAVEFORM = "waveform"
        const val OUT_DURATIONS = "durations"

        /** Flatten any numeric OnnxTensor (float or int64, any rank) to a FloatArray. */
        fun flatFloats(value: OnnxValue): FloatArray {
            val tensor = value as OnnxTensor
            return try {
                val fb = tensor.floatBuffer
                FloatArray(fb.remaining()).also { fb.get(it) }
            } catch (_: Exception) {
                // Integer tensors (e.g. int64 durations) expose a longBuffer instead.
                val lb = tensor.longBuffer
                FloatArray(lb.remaining()) { lb.get(it).toFloat() }
            }
        }
    }
}
