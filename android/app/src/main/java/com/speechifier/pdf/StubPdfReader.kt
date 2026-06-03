package com.speechifier.pdf

import android.net.Uri
import com.speechifier.text.Segmenter
import kotlinx.coroutines.delay

/**
 * Interim [PdfReader] returning a fixed sample document, so the reader UI is
 * usable before PdfBox-Android extraction ([PdfExtractor], task #7) is wired.
 * Ignores [uri].
 */
class StubPdfReader : PdfReader {
    override suspend fun extract(uri: Uri): Segmenter.Document {
        delay(150)
        val blocks = listOf(
            "Speechifier reads your documents aloud. Each word lights up as it is spoken. " +
                "This sample stands in for a real PDF until on-device extraction is wired up.",
            "Tap any sentence to jump to it. Tap a word to seek within the current sentence. " +
                "Use the controls below to play, pause, and move between sentences.",
            "Pick a voice and adjust the speed to taste. On a real device, the Kokoro model " +
                "synthesizes this text entirely offline, with no server involved.",
        )
        return Segmenter.buildDocument(title = "Sample Document", blocks = blocks)
    }
}
