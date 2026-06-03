package com.speechifier.pdf

import android.net.Uri
import com.speechifier.text.Segmenter

/**
 * Extracts a reflowed [Segmenter.Document] from a picked PDF. The real
 * implementation ([PdfExtractor], task #7) uses PdfBox-Android; the ViewModel
 * depends only on this interface.
 */
interface PdfReader {
    /**
     * Read and segment the PDF at [uri].
     * @throws PdfException for unreadable, empty, or text-free PDFs (mirrors the
     *   server's 400/422 responses).
     */
    suspend fun extract(uri: Uri): Segmenter.Document
}

/** A user-facing PDF failure; [message] is shown in the UI (mirrors server errors). */
class PdfException(message: String) : Exception(message)
