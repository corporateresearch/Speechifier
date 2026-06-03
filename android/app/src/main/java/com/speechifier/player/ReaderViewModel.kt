package com.speechifier.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speechifier.audio.ClockTtsPlayer
import com.speechifier.audio.TtsPlayer
import com.speechifier.pdf.PdfException
import com.speechifier.pdf.PdfReader
import com.speechifier.tts.Voices
import com.speechifier.tts.WordTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Coarse screen phase for the reader. */
enum class ReaderPhase { Empty, Loading, Ready, Error }

/** Immutable UI state rendered by [com.speechifier.ui.ReaderScreen]. */
data class ReaderUiState(
    val phase: ReaderPhase = ReaderPhase.Empty,
    val title: String = "",
    val sentences: List<String> = emptyList(),
    val paragraphs: List<List<Int>> = emptyList(),
    val currentSentence: Int = -1,
    /** Words of the current sentence once synthesized (empty otherwise). */
    val activeWords: List<WordTimer.Word> = emptyList(),
    val activeWordIndex: Int = -1,
    val isPlaying: Boolean = false,
    val voiceId: String = Voices.DEFAULT_VOICE,
    val speed: Float = 1.0f,
    val error: String? = null,
)

/**
 * Drives the reader: loads a PDF, synthesizes sentences on demand (caching +
 * prefetching the next), plays them in order, and maps playback position to the
 * active word for highlighting. This is the on-device equivalent of the web
 * app's `app.js` playback engine.
 *
 * Audio + synthesis are injected via interfaces; the defaults are the interim
 * [StubSynthesizer]/[ClockTtsPlayer] so the screen runs before Kokoro (task #6)
 * and PdfBox (task #7) are wired.
 */
class ReaderViewModel(
    private val synthesizer: Synthesizer,
    private val pdfReader: PdfReader,
    playerProvider: (CoroutineScope) -> TtsPlayer = { ClockTtsPlayer(it) },
) : ViewModel() {

    private val player: TtsPlayer = playerProvider(viewModelScope)
    private val cache = SentenceCache()

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    /** Guards the in-flight "start a sentence" coroutine so taps can't overlap. */
    private var playJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        player.setOnComplete { advanceAfterCompletion() }
        // Position -> active word index.
        viewModelScope.launch {
            player.positionSec.collect { pos ->
                val words = _ui.value.activeWords
                val idx = WordTimer.activeWordIndex(words, pos)
                if (idx != _ui.value.activeWordIndex) {
                    _ui.update { it.copy(activeWordIndex = idx) }
                }
            }
        }
        viewModelScope.launch {
            player.isPlaying.collect { playing ->
                if (playing != _ui.value.isPlaying) _ui.update { it.copy(isPlaying = playing) }
            }
        }
    }

    // ---- PDF loading -------------------------------------------------------

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(phase = ReaderPhase.Loading, error = null) }
            try {
                val doc = pdfReader.extract(uri)
                player.stop()
                _ui.update {
                    ReaderUiState(
                        phase = ReaderPhase.Ready,
                        title = doc.title,
                        sentences = doc.sentences,
                        paragraphs = doc.paragraphs,
                        voiceId = it.voiceId,
                        speed = it.speed,
                    )
                }
            } catch (e: PdfException) {
                _ui.update { it.copy(phase = ReaderPhase.Error, error = e.message) }
            }
        }
    }

    // ---- Transport ---------------------------------------------------------

    fun togglePlayPause() {
        val s = _ui.value
        if (s.phase != ReaderPhase.Ready || s.sentences.isEmpty()) return
        when {
            s.currentSentence < 0 -> startSentence(0)
            player.isPlaying.value -> player.pause()
            else -> player.play()
        }
    }

    fun next() {
        val s = _ui.value
        if (s.currentSentence + 1 < s.sentences.size) startSentence(s.currentSentence + 1)
    }

    fun previous() {
        val s = _ui.value
        if (s.currentSentence > 0) startSentence(s.currentSentence - 1)
    }

    /** Tap a sentence to jump there and play (mirrors app.js). */
    fun jumpToSentence(index: Int) {
        if (index in _ui.value.sentences.indices) startSentence(index)
    }

    /** Tap a word to seek within the current sentence. */
    fun seekToWord(wordIndex: Int) {
        val words = _ui.value.activeWords
        words.getOrNull(wordIndex)?.let { player.seekTo(it.start) }
    }

    // ---- Voice & speed -----------------------------------------------------

    fun setVoice(voiceId: String) {
        if (voiceId == _ui.value.voiceId) return
        _ui.update { it.copy(voiceId = voiceId) }
        restartCurrentForSettingsChange()
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        if (clamped == _ui.value.speed) return
        _ui.update { it.copy(speed = clamped) }
        restartCurrentForSettingsChange()
    }

    /** Re-synthesize the current sentence under the new voice/speed, preserving play state. */
    private fun restartCurrentForSettingsChange() {
        val cur = _ui.value.currentSentence
        if (cur >= 0) startSentence(cur, autoPlay = player.isPlaying.value)
    }

    // ---- Core: synthesize + start one sentence -----------------------------

    private fun startSentence(index: Int, autoPlay: Boolean = true) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val voice = _ui.value.voiceId
            val speed = _ui.value.speed
            val result = synthesize(index, voice, speed)
            _ui.update {
                it.copy(currentSentence = index, activeWords = result.words, activeWordIndex = -1)
            }
            player.load(result.pcm, result.sampleRate, result.durationSec)
            if (autoPlay) player.play()
            prefetch(index + 1, voice, speed)
        }
    }

    private suspend fun synthesize(index: Int, voiceId: String, speed: Float): SynthesisResult {
        cache.get(index, voiceId, speed)?.let { return it }
        val text = _ui.value.sentences[index]
        val result = synthesizer.synthesize(text, voiceId, speed)
        cache.put(index, voiceId, speed, result)
        return result
    }

    private fun prefetch(index: Int, voiceId: String, speed: Float) {
        if (index !in _ui.value.sentences.indices) return
        if (cache.get(index, voiceId, speed) != null) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            runCatching { synthesize(index, voiceId, speed) }
        }
    }

    private fun advanceAfterCompletion() {
        val s = _ui.value
        if (s.currentSentence + 1 < s.sentences.size) {
            startSentence(s.currentSentence + 1)
        } else {
            _ui.update { it.copy(isPlaying = false, activeWordIndex = -1) }
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
