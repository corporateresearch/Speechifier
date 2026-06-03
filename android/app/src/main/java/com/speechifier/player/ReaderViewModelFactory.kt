package com.speechifier.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.speechifier.pdf.PdfExtractor
import com.speechifier.pdf.PdfReader

/**
 * Wires the [ReaderViewModel]'s dependencies.
 *
 * PDF extraction is real ([PdfExtractor], PdfBox-Android). Synthesis still uses
 * the interim [StubSynthesizer] until KokoroEngine (task #6) lands — swap it here
 * and no call site changes.
 */
class ReaderViewModelFactory(
    context: Context,
    private val synthesizer: Synthesizer = StubSynthesizer(),
    private val pdfReader: PdfReader = PdfExtractor(context),
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return ReaderViewModel(synthesizer = synthesizer, pdfReader = pdfReader) as T
    }
}
