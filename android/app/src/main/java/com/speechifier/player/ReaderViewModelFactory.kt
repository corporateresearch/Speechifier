package com.speechifier.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.speechifier.assets.AssetStore
import com.speechifier.audio.AudioTrackPlayer
import com.speechifier.pdf.PdfExtractor
import com.speechifier.pdf.PdfReader
import com.speechifier.tts.KokoroEngine
import com.speechifier.tts.KokoroVocab
import com.speechifier.tts.PhonemizerFactory
import java.io.FileInputStream

/**
 * Wires the [ReaderViewModel]'s dependencies.
 *
 * PDF extraction is real ([PdfExtractor], PdfBox-Android). Synthesis uses
 * KokoroEngine and playback uses AudioTrackPlayer.
 */
class ReaderViewModelFactory(
    context: Context,
    private val pdfReader: PdfReader = PdfExtractor(context),
) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        
        val store = AssetStore(appContext)
        val synthesizer = KokoroEngine(
            modelPath = store.model.absolutePath,
            voicesDir = store.voicesDir,
            vocab = KokoroVocab.fromJson(FileInputStream(store.vocab)),
            phonemizers = PhonemizerFactory.create(store)
        )

        return ReaderViewModel(
            synthesizer = synthesizer,
            pdfReader = pdfReader,
            playerProvider = { scope -> AudioTrackPlayer(scope) }
        ) as T
    }
}
